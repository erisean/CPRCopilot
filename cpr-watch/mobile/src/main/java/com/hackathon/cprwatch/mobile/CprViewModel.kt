package com.hackathon.cprwatch.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.cprwatch.shared.CprDataPoint
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

enum class ScreenState { IDLE, LIVE, SCORECARD }

data class MobileUiState(
    val screen: ScreenState = ScreenState.IDLE,
    val currentSession: CprSession? = null,
    val completedSession: CprSession? = null,
    val pastSessions: List<CprSession> = emptyList(),
    val latestDataPoint: CprDataPoint? = null,
    val isSimulating: Boolean = false
)

class CprViewModel : ViewModel() {

    private var simulationJob: Job? = null
    private val _completedSession = MutableStateFlow<CprSession?>(null)

    val uiState: StateFlow<MobileUiState> = combine(
        CprRepository.currentSession,
        CprRepository.pastSessions,
        CprRepository.simulating,
        _completedSession
    ) { current, past, simulating, completed ->
        val screen = when {
            current != null -> ScreenState.LIVE
            completed != null -> ScreenState.SCORECARD
            else -> ScreenState.IDLE
        }
        MobileUiState(
            screen = screen,
            currentSession = current,
            completedSession = completed,
            pastSessions = past,
            latestDataPoint = current?.dataPoints?.lastOrNull(),
            isSimulating = simulating
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MobileUiState())

    fun dismissScorecard() {
        _completedSession.value = null
    }

    fun startSimulation() {
        if (simulationJob?.isActive == true) return
        _completedSession.value = null
        CprRepository.setSimulating(true)
        CprRepository.startSession()

        simulationJob = viewModelScope.launch {
            var count = 0
            while (isActive) {
                count++
                val phase = count / 30.0
                val rateDrift = (sin(phase) * 15).toInt()
                val depthDrift = (sin(phase * 0.7) * 1.2).toFloat()

                val rate = (110 + rateDrift + Random.nextInt(-3, 4)).coerceIn(80, 140)
                val depth = (5.5f + depthDrift + Random.nextFloat() * 0.4f - 0.2f).coerceIn(2f, 8f)

                val feedback = when {
                    rate < 100 -> "Push faster"
                    rate > 120 -> "Slow down"
                    depth < 5.0f -> "Push harder"
                    depth > 6.0f -> "Ease up"
                    else -> "Good compressions!"
                }

                CprRepository.addDataPoint(
                    CprDataPoint(
                        timestampMs = System.currentTimeMillis(),
                        rate = rate,
                        depthCm = depth,
                        feedback = feedback
                    )
                )

                delay(545)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _completedSession.value = CprRepository.currentSession.value
        CprRepository.endSession()
        CprRepository.setSimulating(false)
    }
}
