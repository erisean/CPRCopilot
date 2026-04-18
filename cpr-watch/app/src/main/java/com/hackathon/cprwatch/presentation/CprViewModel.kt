package com.hackathon.cprwatch.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackathon.cprwatch.data.DataSender
import com.hackathon.cprwatch.haptic.HapticCoach
import com.hackathon.cprwatch.sensor.CompressionDetector
import com.hackathon.cprwatch.sensor.CompressionFeedback
import com.hackathon.cprwatch.sensor.CompressionMetrics
import com.hackathon.cprwatch.shared.CprDataPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class CprViewModel(application: Application) : AndroidViewModel(application) {

    private val detector = CompressionDetector(application)
    private val haptic = HapticCoach(application)
    private val dataSender = DataSender(application)

    private val _uiState = MutableStateFlow(CprUiState())
    val uiState: StateFlow<CprUiState> = _uiState

    private var sessionActive = false

    init {
        observeDetector()
    }

    fun startSession() {
        if (sessionActive) return
        sessionActive = true

        _uiState.value = _uiState.value.copy(
            isActive = true,
            feedbackMessage = "Start compressions"
        )

        detector.start()
        haptic.startMetronome(viewModelScope)

        viewModelScope.launch {
            try { dataSender.sendSessionStart() } catch (_: Exception) {}
        }

    }

    fun stopSession() {
        sessionActive = false
        detector.stop()
        haptic.stopMetronome()
        _uiState.value = CprUiState()

        viewModelScope.launch {
            try { dataSender.sendSessionStop() } catch (_: Exception) {}
        }
    }

    private fun observeDetector() {
        detector.metrics
            .onEach { metrics ->
                val message = feedbackMessage(metrics)

                _uiState.value = _uiState.value.copy(
                    isActive = sessionActive,
                    rate = metrics.rate,
                    depthCm = metrics.depthCm,
                    isCompressing = metrics.isCompressing,
                    feedback = metrics.feedback,
                    feedbackMessage = if (sessionActive) message else "Tap to start"
                )

                if (!sessionActive) return@onEach

                if (metrics.isCompressing && metrics.rate > 0) {
                    viewModelScope.launch {
                        try {
                            dataSender.sendDataPoint(
                                CprDataPoint(
                                    timestampMs = System.currentTimeMillis(),
                                    rate = metrics.rate,
                                    depthCm = metrics.depthCm,
                                    feedback = message
                                )
                            )
                        } catch (_: Exception) {}
                    }
                }

                if (metrics.feedback != CompressionFeedback.GOOD &&
                    metrics.feedback != CompressionFeedback.IDLE &&
                    metrics.feedback != CompressionFeedback.CALIBRATING &&
                    metrics.isCompressing
                ) {
                    haptic.pulseWarning()
                }
            }
            .launchIn(viewModelScope)

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
    }

    private fun feedbackMessage(metrics: CompressionMetrics): String {
        return when (metrics.feedback) {
            CompressionFeedback.IDLE -> "Tap to start"
            CompressionFeedback.CALIBRATING -> "Calibrating…"
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
    val accelTimestampMs: Long = 0L
)
