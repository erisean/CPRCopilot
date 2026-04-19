package com.hackathon.cprwatch.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.cprwatch.shared.CompressionEvent
import com.hackathon.cprwatch.shared.CprSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

enum class ScreenState { IDLE, LIVE, SCORECARD, HISTORY, HISTORY_DETAIL }

data class MobileUiState(
    val screen: ScreenState = ScreenState.IDLE,
    val currentSession: CprSession? = null,
    val completedSession: CprSession? = null,
    val pastSessions: List<CprSession> = emptyList(),
    val latestEvent: CompressionEvent? = null,
    val isSimulating: Boolean = false,
    val isListening: Boolean = false,
    val watchConnected: Boolean = false,
    val watchName: String? = null,
    val selectedHistorySession: CprSession? = null,
    val surfaceCalibrated: Boolean = false,
    val surfaceCalibrationProgress: Float = 0f,
    val surfaceProfile: MobileSurfaceProfile? = null
)

class CprViewModel(application: Application) : AndroidViewModel(application) {

    private var simulationJob: Job? = null
    private val _completedSession = MutableStateFlow<CprSession?>(null)
    private val _screenOverride = MutableStateFlow<ScreenState?>(null)
    private val _selectedHistorySession = MutableStateFlow<CprSession?>(null)
    private val connectionMonitor = WatchConnectionMonitor(application)
    private val surfaceCalibrator = MobileSurfaceCalibrator()
    private val _surfaceState = MutableStateFlow(Triple(false, 0f, null as MobileSurfaceProfile?))

    private var lastEventCount = 0

    init {
        viewModelScope.launch {
            connectionMonitor.startMonitoring()
        }
        viewModelScope.launch {
            CprRepository.currentSession.collect { session ->
                if (session == null) {
                    lastEventCount = 0
                    surfaceCalibrator.reset()
                    _surfaceState.value = Triple(false, 0f, null)
                    return@collect
                }
                val events = session.compressionEvents
                if (events.size > lastEventCount) {
                    for (i in lastEventCount until events.size) {
                        surfaceCalibrator.addEvent(events[i])
                    }
                    lastEventCount = events.size
                    _surfaceState.value = Triple(
                        surfaceCalibrator.isCalibrated,
                        surfaceCalibrator.progress,
                        surfaceCalibrator.profile
                    )
                }
            }
        }
    }

    val uiState: StateFlow<MobileUiState> = combine(
        CprRepository.currentSession,
        CprRepository.pastSessions,
        CprRepository.simulating,
        CprRepository.listening,
        _completedSession,
        connectionMonitor.state,
        _screenOverride,
        _selectedHistorySession,
        _surfaceState
    ) { values ->
        val current = values[0] as CprSession?
        val past = @Suppress("UNCHECKED_CAST") (values[1] as List<CprSession>)
        val simulating = values[2] as Boolean
        val listening = values[3] as Boolean
        val completed = values[4] as CprSession?
        val connection = values[5] as WatchConnectionState
        val override = values[6] as ScreenState?
        val historySession = values[7] as CprSession?
        val surface = @Suppress("UNCHECKED_CAST") (values[8] as Triple<Boolean, Float, MobileSurfaceProfile?>)

        val screen = override ?: when {
            current != null -> ScreenState.LIVE
            completed != null -> ScreenState.SCORECARD
            else -> ScreenState.IDLE
        }
        MobileUiState(
            screen = screen,
            currentSession = current,
            completedSession = completed,
            pastSessions = past,
            latestEvent = current?.compressionEvents?.lastOrNull(),
            isSimulating = simulating,
            isListening = listening,
            watchConnected = connection.isConnected,
            watchName = connection.watchName,
            selectedHistorySession = historySession,
            surfaceCalibrated = surface.first,
            surfaceCalibrationProgress = surface.second,
            surfaceProfile = surface.third
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MobileUiState())

    fun startSession() {
        _completedSession.value = null
        surfaceCalibrator.reset()
        lastEventCount = 0
        _surfaceState.value = Triple(false, 0f, null)
        CprRepository.startListening()
        CprRepository.startSession()
    }

    fun stopSession() {
        _completedSession.value = CprRepository.currentSession.value
        CprRepository.endSession()
        CprRepository.stopListening()
        CprRepository.setSimulating(false)
        simulationJob?.cancel()
        simulationJob = null
    }

    fun dismissScorecard() {
        _completedSession.value = null
        _screenOverride.value = null
    }

    fun showHistory() {
        _screenOverride.value = ScreenState.HISTORY
    }

    fun showHistoryDetail(session: CprSession) {
        _selectedHistorySession.value = session
        _screenOverride.value = ScreenState.HISTORY_DETAIL
    }

    fun backFromHistory() {
        _screenOverride.value = null
        _selectedHistorySession.value = null
    }

    fun backFromHistoryDetail() {
        _screenOverride.value = ScreenState.HISTORY
        _selectedHistorySession.value = null
    }

    fun startSimulation() {
        if (simulationJob?.isActive == true) return
        _completedSession.value = null
        CprRepository.setSimulating(true)
        CprRepository.startListening()
        CprRepository.startSession()

        simulationJob = viewModelScope.launch {
            var count = 0
            while (isActive) {
                count++
                val now = System.currentTimeMillis()
                val phase = count / 30.0
                val rateDrift = (sin(phase) * 15).toInt()
                val depthDrift = (sin(phase * 0.7) * 12).toFloat()

                val intervalMs = (545 + Random.nextInt(-20, 21))
                val rate = (110 + rateDrift + Random.nextInt(-3, 4)).coerceIn(80, 140)
                val depthMm = (55f + depthDrift + Random.nextFloat() * 4f - 2f).coerceIn(20f, 80f)
                val recoilPct = (97f + Random.nextFloat() * 4f - 2f).coerceIn(80f, 100f)

                val instruction = when {
                    rate < 100 -> "faster"
                    rate > 120 -> "slower"
                    depthMm < 50 -> "push_harder"
                    depthMm > 60 -> "ease_up"
                    else -> "none"
                }

                CprRepository.addCompressionEvent(
                    CompressionEvent(
                        compressionIdx = count,
                        timestampMs = now,
                        intervalMs = intervalMs,
                        instantaneousRateBpm = 60000f / intervalMs,
                        rollingRateBpm = rate.toFloat(),
                        estimatedDepthMm = depthMm,
                        recoilMm = depthMm * recoilPct / 100f,
                        recoilPct = recoilPct,
                        dutyCycle = 0.5f,
                        peakAccelMps2 = (7f + Random.nextFloat() * 3f),
                        wristAngleDeg = Random.nextFloat() * 10f,
                        rescuerHrBpm = if (count % 10 == 0) (90 + count / 5).coerceAtMost(160) else null,
                        isQualityGood = instruction == "none",
                        instruction = instruction,
                        instructionPriority = when (instruction) {
                            "none" -> null
                            "faster", "slower" -> 2
                            "push_harder", "ease_up" -> 3
                            else -> null
                        },
                    ),
                )

                delay(intervalMs.toLong())
            }
        }
    }
}
