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

    fun startSession() {
        if (sessionActive) return
        sessionActive = true

        detector.start(viewModelScope)
        haptic.startMetronome(viewModelScope)

        viewModelScope.launch {
            try { dataSender.sendSessionStart() } catch (_: Exception) {}
        }

        // Listen for compression events (Layer 2) and send to phone
        detector.compressionEvents
            .onEach { event ->
                viewModelScope.launch {
                    try { dataSender.sendCompressionEvent(event) } catch (_: Exception) {}
                }

                // Also send legacy CprDataPoint for phone UI compatibility
                viewModelScope.launch {
                    try {
                        dataSender.sendDataPoint(
                            CprDataPoint(
                                timestampMs = event.timestampMs,
                                rate = event.rollingRateBpm.toInt(),
                                depthCm = event.estimatedDepthMm / 10f,
                                feedback = instructionToFeedback(event.instruction)
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
            .launchIn(viewModelScope)

        // Listen for metrics (derived from Layer 2) for watch UI
        detector.metrics
            .onEach { metrics ->
                _uiState.value = CprUiState(
                    isActive = true,
                    rate = metrics.rate,
                    depthCm = metrics.depthCm,
                    isCompressing = metrics.isCompressing,
                    feedback = metrics.feedback,
                    feedbackMessage = metrics.feedback.message
                )

                if (metrics.feedback != CompressionFeedback.GOOD &&
                    metrics.feedback != CompressionFeedback.IDLE &&
                    metrics.isCompressing
                ) {
                    haptic.pulseWarning()
                }
            }
            .launchIn(viewModelScope)
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

    private fun instructionToFeedback(instruction: String): String {
        return when (instruction) {
            "none" -> "Good compressions!"
            "faster" -> "Push faster"
            "slower" -> "Slow down"
            "push_harder" -> "Push harder"
            "ease_up" -> "Ease up"
            "let_chest_up" -> "Let chest recoil"
            "resume_compressions" -> "Resume compressions!"
            "switch_rescuers" -> "Switch rescuers"
            "consider_switching" -> "Consider switching"
            "stay_strong" -> "Stay strong!"
            else -> "Good compressions!"
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
    val feedbackMessage: String = "Tap to start"
)
