package com.hackathon.cprwatch.sensor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

class MockCompressionDetector {

    private val _metrics = MutableStateFlow(CompressionMetrics())
    val metrics: StateFlow<CompressionMetrics> = _metrics

    private var job: Job? = null
    private var compressionCount = 0

    fun start(scope: CoroutineScope) {
        compressionCount = 0
        job = scope.launch {
            // Brief delay before "compressions start"
            delay(1500)

            while (isActive) {
                compressionCount++

                // Simulate varying quality over time using a slow sine wave
                val phase = compressionCount / 30.0
                val rateDrift = (sin(phase) * 15).toInt()
                val depthDrift = (sin(phase * 0.7) * 1.2).toFloat()

                val rate = (110 + rateDrift + Random.nextInt(-3, 4))
                    .coerceIn(80, 140)
                val depth = (5.5f + depthDrift + Random.nextFloat() * 0.4f - 0.2f)
                    .coerceIn(2f, 8f)

                val feedback = when {
                    rate < 100 -> CompressionFeedback.TOO_SLOW
                    rate > 120 -> CompressionFeedback.TOO_FAST
                    depth < 5.0f -> CompressionFeedback.TOO_SHALLOW
                    depth > 6.0f -> CompressionFeedback.TOO_DEEP
                    else -> CompressionFeedback.GOOD
                }

                _metrics.value = CompressionMetrics(
                    rate = rate,
                    depthCm = depth,
                    isCompressing = true,
                    feedback = feedback
                )

                // ~110 bpm = ~545ms per compression
                delay(545)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _metrics.value = CompressionMetrics()
    }
}
