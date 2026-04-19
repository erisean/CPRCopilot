package com.hackathon.cprwatch.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

data class CompressionMetrics(
    val rate: Int = 0,
    val depthCm: Float = 0f,
    val isCompressing: Boolean = false,
    val feedback: CompressionFeedback = CompressionFeedback.IDLE
)

data class AccelerometerSample(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val magnitude: Float = 0f,
    val timestampMs: Long = 0L
)

enum class CompressionFeedback {
    IDLE,
    CALIBRATING,
    GOOD,
    TOO_SLOW,
    TOO_FAST,
    TOO_SHALLOW,
    TOO_DEEP
}

class CompressionDetector(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _metrics = MutableStateFlow(CompressionMetrics())
    val metrics: StateFlow<CompressionMetrics> = _metrics

    private val _liveSample = MutableStateFlow(AccelerometerSample())
    val liveSample: StateFlow<AccelerometerSample> = _liveSample

    // Peak detection state
    private val compressionTimestamps = mutableListOf<Long>()
    private var lastPeakTime = 0L
    private var velocity = 0f
    private var displacement = 0f
    private var peakDisplacement = 0f
    private var isInCompression = false
    private var lastTimestamp = 0L
    private var gravityInitialized = false
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var calibrationStartMs = 0L
    private var isCalibrating = true

    // Thresholds
    private val peakAccelThreshold = 3.0f // m/s² to detect a compression push
    private val minCompressionIntervalMs = 300L // max ~200 bpm
    private val maxCompressionIntervalMs = 1200L // min ~50 bpm
    private val idleTimeoutMs = 3000L
    private val gravityTimeConstantSec = 0.25f
    private val calibrationDurationMs = 500L

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
        gravityInitialized = false
        gravityX = 0f
        gravityY = 0f
        gravityZ = 0f
        calibrationStartMs = 0L
        isCalibrating = true
        _metrics.value = CompressionMetrics()
        _liveSample.value = AccelerometerSample()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val now = event.timestamp // nanoseconds
        if (lastTimestamp == 0L) {
            lastTimestamp = now
            return
        }

        val dt = (now - lastTimestamp) / 1_000_000_000f // seconds
        lastTimestamp = now

        if (dt <= 0 || dt > 0.1f) return

        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        if (!gravityInitialized) {
            gravityX = rawX
            gravityY = rawY
            gravityZ = rawZ
            gravityInitialized = true
            calibrationStartMs = now / 1_000_000
        }

        // Low-pass gravity estimate, then subtract it to get linear acceleration.
        val alpha = gravityTimeConstantSec / (gravityTimeConstantSec + dt)
        gravityX = alpha * gravityX + (1 - alpha) * rawX
        gravityY = alpha * gravityY + (1 - alpha) * rawY
        gravityZ = alpha * gravityZ + (1 - alpha) * rawZ

        val currentTimeMs = now / 1_000_000

        // During calibration, keep updating gravity but suppress detection and live output
        if (isCalibrating) {
            if (currentTimeMs - calibrationStartMs < calibrationDurationMs) {
                _metrics.value = CompressionMetrics(feedback = CompressionFeedback.CALIBRATING)
                return
            }
            isCalibrating = false
        }

        val ax = rawX - gravityX
        val ay = rawY - gravityY
        val az = rawZ - gravityZ
        val accelMagnitude = sqrt(ax * ax + ay * ay + az * az)

        _liveSample.value = AccelerometerSample(
            x = ax,
            y = ay,
            z = az,
            magnitude = accelMagnitude,
            timestampMs = now / 1_000_000
        )

        // Integrate acceleration to get velocity, then displacement
        velocity += accelMagnitude * dt
        displacement += velocity * dt


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
            //reset()
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
