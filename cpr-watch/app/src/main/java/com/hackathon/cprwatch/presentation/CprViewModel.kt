package com.hackathon.cprwatch.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.cprwatch.data.DataSender
import com.hackathon.cprwatch.haptic.HapticCoach
import com.hackathon.cprwatch.sensor.CompressionDetector
import com.hackathon.cprwatch.sensor.CompressionFeedback
import com.hackathon.cprwatch.sensor.CompressionMetrics
import com.hackathon.cprwatch.sensor.CompressionResult
import com.hackathon.cprwatch.sensor.HeartRateMonitor
import com.hackathon.cprwatch.shared.CompressionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CprViewModel(application: Application) : AndroidViewModel(application) {

    private val detector = CompressionDetector(application)
    private val haptic = HapticCoach(application)
    private val heartRateMonitor = HeartRateMonitor(application)
    private val dataSender = DataSender(application)

    private val _uiState = MutableStateFlow(CprUiState())
    val uiState: StateFlow<CprUiState> = _uiState

    private var sessionActive = false
    private var messagesSent = 0
    private var lastSendError: String? = null

    init {
        observeDetector()
    }

    fun startSession() {
        if (sessionActive) return
        sessionActive = true
        messagesSent = 0
        lastSendError = null

        _uiState.value = _uiState.value.copy(
            isActive = true,
            feedbackMessage = "Start compressions",
            metronomeBeatId = 0L
        )

        detector.start()
        heartRateMonitor.start()
        haptic.startMetronome(viewModelScope)

        viewModelScope.launch {
            try { dataSender.sendSessionStart() } catch (_: Exception) {}
        }
    }

    fun stopSession() {
        sessionActive = false
        detector.stop()
        heartRateMonitor.stop()
        haptic.stopMetronome()
        _uiState.value = CprUiState()

        viewModelScope.launch {
            try { dataSender.sendSessionStop() } catch (_: Exception) {}
        }
    }

    private fun observeDetector() {
        // Update watch UI from metrics (continuous)
        detector.metrics
            .onEach { metrics ->
                _uiState.value = _uiState.value.copy(
                    isActive = sessionActive,
                    rate = metrics.rate,
                    depthCm = metrics.depthCm,
                    isCompressing = metrics.isCompressing,
                    feedback = metrics.feedback,
                    feedbackMessage = if (sessionActive) feedbackMessage(metrics) else "Tap to start"
                )

            }
            .launchIn(viewModelScope)

        // Send one event per compression to the phone
        detector.compressionCompleted
            .onEach { raw ->
                if (!sessionActive) return@onEach
                val result = raw.copy(rescuerHrBpm = heartRateMonitor.heartRate.value.takeIf { it > 0 })

                viewModelScope.launch {
                    try {
                        dataSender.sendCompressionEvent(resultToEvent(result))
                        messagesSent++
                        lastSendError = null
                    } catch (e: Exception) {
                        lastSendError = e.message ?: "Send failed"
                    }
                    _uiState.value = _uiState.value.copy(
                        messagesSent = messagesSent,
                        sendError = lastSendError
                    )
                }
            }
            .launchIn(viewModelScope)

        // Live accelerometer for debug display
        detector.liveSample
            .onEach { sample ->
                _uiState.value = _uiState.value.copy(
                    accelX = sample.x,
                    accelY = sample.y,
                    accelZ = sample.z,
                    accelMagnitude = sample.magnitude,
                    accelTimestampMs = sample.timestampMs
                )
            }
            .launchIn(viewModelScope)

        heartRateMonitor.heartRate
            .onEach { hr ->
                _uiState.value = _uiState.value.copy(rescuerHr = hr)
            }
            .launchIn(viewModelScope)

        haptic.metronomeBeats
            .onEach { beatId ->
                if (!sessionActive) return@onEach
                _uiState.value = _uiState.value.copy(metronomeBeatId = beatId)
            }
            .launchIn(viewModelScope)
    }

    private fun resultToEvent(result: CompressionResult): CompressionEvent {
        val feedback = _uiState.value.feedback
        return CompressionEvent(
            compressionIdx = result.compressionIdx,
            timestampMs = result.timestampMs,
            intervalMs = result.intervalMs,
            instantaneousRateBpm = if (result.intervalMs > 0) 60000f / result.intervalMs else 0f,
            rollingRateBpm = result.rate.toFloat(),
            estimatedDepthMm = result.depthMm,
            recoilMm = result.recoilMm,
            recoilPct = result.recoilPct,
            dutyCycle = result.dutyCycle,
            peakAccelMps2 = result.peakAccel,
            wristAngleDeg = 0f,
            rescuerHrBpm = result.rescuerHrBpm,
            isQualityGood = feedback == CompressionFeedback.GOOD,
            instruction = when (feedback) {
                CompressionFeedback.GOOD -> "none"
                CompressionFeedback.TOO_SLOW -> "faster"
                CompressionFeedback.TOO_FAST -> "slower"
                CompressionFeedback.TOO_SHALLOW -> "push_harder"
                CompressionFeedback.TOO_DEEP -> "ease_up"
                CompressionFeedback.IDLE -> "none"
                CompressionFeedback.CALIBRATING -> "none"
            },
            instructionPriority = when (feedback) {
                CompressionFeedback.GOOD, CompressionFeedback.CALIBRATING, CompressionFeedback.IDLE -> null
                CompressionFeedback.TOO_SLOW, CompressionFeedback.TOO_FAST -> 2
                CompressionFeedback.TOO_SHALLOW, CompressionFeedback.TOO_DEEP -> 3
            }
        )
    }

    private fun feedbackMessage(metrics: CompressionMetrics): String {
        return when (metrics.feedback) {
            CompressionFeedback.IDLE -> "Resume compressions to continue"
            CompressionFeedback.CALIBRATING -> "Begin compressions"
            CompressionFeedback.GOOD -> "Good compressions!"
            CompressionFeedback.TOO_SLOW -> "Push faster"
            CompressionFeedback.TOO_FAST -> "Slow down"
            CompressionFeedback.TOO_SHALLOW -> "Push harder"
            CompressionFeedback.TOO_DEEP -> "Ease up"
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSession()
    }
}

data class CprUiState(
    val isActive: Boolean = false,
    val rate: Int = 0,
    val depthCm: Float = 0f,
    val isCompressing: Boolean = false,
    val feedback: CompressionFeedback = CompressionFeedback.IDLE,
    val feedbackMessage: String = "Tap to start",
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val accelMagnitude: Float = 0f,
    val accelTimestampMs: Long = 0L,
    val rescuerHr: Int = 0,
    val messagesSent: Int = 0,
    val sendError: String? = null,
    val metronomeBeatId: Long = 0L
)
