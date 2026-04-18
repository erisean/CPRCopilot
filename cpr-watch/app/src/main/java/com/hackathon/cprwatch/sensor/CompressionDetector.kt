package com.hackathon.cprwatch.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class CompressionMetrics(
    val rate: Int = 0,
    val depthCm: Float = 0f,
    val isCompressing: Boolean = false,
    val feedback: CompressionFeedback = CompressionFeedback.IDLE
)

enum class CompressionFeedback {
    IDLE,
    GOOD,
    TOO_SLOW,
    TOO_FAST,
    TOO_SHALLOW,
    TOO_DEEP
}

class CompressionDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val _metrics = MutableStateFlow(CompressionMetrics())
    val metrics: StateFlow<CompressionMetrics> = _metrics

    // Peak detection state
    private val compressionTimestamps = mutableListOf<Long>()
    private var lastPeakTime = 0L
    private var velocity = 0f
    private var displacement = 0f
    private var peakDisplacement = 0f
    private var isInCompression = false
    private var lastTimestamp = 0L

    // Thresholds
    private val peakAccelThreshold = 3.0f // m/s² to detect a compression push
    private val minCompressionIntervalMs = 300L // max ~200 bpm
    private val maxCompressionIntervalMs = 1200L // min ~50 bpm
    private val idleTimeoutMs = 3000L

    // AHA guidelines
    private val targetRateMin = 100
    private val targetRateMax = 120
    private val targetDepthMinCm = 5.0f
    private val targetDepthMaxCm = 6.0f

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        reset()
    }

    private fun reset() {
        compressionTimestamps.clear()
        lastPeakTime = 0L
        velocity = 0f
        displacement = 0f
        peakDisplacement = 0f
        isInCompression = false
        lastTimestamp = 0L
        _metrics.value = CompressionMetrics()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        val now = event.timestamp // nanoseconds
        if (lastTimestamp == 0L) {
            lastTimestamp = now
            return
        }

        val dt = (now - lastTimestamp) / 1_000_000_000f // seconds
        lastTimestamp = now

        if (dt <= 0 || dt > 0.1f) return

        // Use the magnitude of acceleration as a proxy for vertical force
        // In practice the watch orientation varies, so magnitude is more robust
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        val accelMagnitude = sqrt(ax * ax + ay * ay + az * az)

        // Integrate acceleration to get velocity, then displacement
        velocity += accelMagnitude * dt
        displacement += velocity * dt

        val currentTimeMs = now / 1_000_000

        if (accelMagnitude > peakAccelThreshold && !isInCompression) {
            val timeSinceLastPeak = currentTimeMs - lastPeakTime

            if (lastPeakTime == 0L || timeSinceLastPeak > minCompressionIntervalMs) {
                isInCompression = true
                peakDisplacement = displacement

                if (lastPeakTime > 0 && timeSinceLastPeak < maxCompressionIntervalMs) {
                    compressionTimestamps.add(currentTimeMs)
                    pruneOldTimestamps(currentTimeMs)
                    updateMetrics()
                } else if (lastPeakTime == 0L || timeSinceLastPeak >= maxCompressionIntervalMs) {
                    compressionTimestamps.clear()
                    compressionTimestamps.add(currentTimeMs)
                }

                lastPeakTime = currentTimeMs
            }
        }

        if (isInCompression && accelMagnitude < peakAccelThreshold * 0.3f) {
            // Compression release phase
            val compDepthM = abs(displacement - peakDisplacement)
            val compDepthCm = compDepthM * 100f

            isInCompression = false
            velocity = 0f
            displacement = 0f
            peakDisplacement = 0f

            _metrics.value = _metrics.value.copy(
                depthCm = compDepthCm.coerceIn(0f, 15f)
            )
        }

        // Check for idle
        if (lastPeakTime > 0 && (currentTimeMs - lastPeakTime) > idleTimeoutMs) {
            reset()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun pruneOldTimestamps(currentTimeMs: Long) {
        val windowMs = 10_000L
        compressionTimestamps.removeAll { currentTimeMs - it > windowMs }
    }

    private fun updateMetrics() {
        if (compressionTimestamps.size < 2) return

        val windowSeconds = (compressionTimestamps.last() - compressionTimestamps.first()) / 1000f
        if (windowSeconds <= 0) return

        val rate = ((compressionTimestamps.size - 1) / windowSeconds * 60).toInt()
        val depth = _metrics.value.depthCm

        val feedback = when {
            rate < targetRateMin -> CompressionFeedback.TOO_SLOW
            rate > targetRateMax -> CompressionFeedback.TOO_FAST
            depth > 0 && depth < targetDepthMinCm -> CompressionFeedback.TOO_SHALLOW
            depth > targetDepthMaxCm -> CompressionFeedback.TOO_DEEP
            else -> CompressionFeedback.GOOD
        }

        _metrics.value = CompressionMetrics(
            rate = rate,
            depthCm = depth,
            isCompressing = true,
            feedback = feedback
        )
    }
}
