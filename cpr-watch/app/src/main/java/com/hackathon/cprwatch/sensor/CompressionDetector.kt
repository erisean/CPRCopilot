package com.hackathon.cprwatch.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max
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

data class CompressionResult(
    val compressionIdx: Int,
    val timestampMs: Long,
    val intervalMs: Int,
    val rate: Int,
    val depthMm: Float,
    val recoilMm: Float,
    val recoilPct: Float,
    val peakAccel: Float,
    val dutyCycle: Float
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

    private val _compressionCompleted = MutableSharedFlow<CompressionResult>(extraBufferCapacity = 64)
    val compressionCompleted: SharedFlow<CompressionResult> = _compressionCompleted

    // Gravity estimation
    private var gravityInitialized = false
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private val gravityTimeConstantSec = 0.25f

    // Calibration
    private var calibrationStartMs = 0L
    private var isCalibrating = true
    private val calibrationDurationMs = 500L

    // Compression cycle state
    private enum class CyclePhase { IDLE, DOWNSTROKE, RECOIL }
    private var phase = CyclePhase.IDLE
    private var velocity = 0f
    private var displacement = 0f
    private var minDisplacement = 0f  // most negative = deepest point
    private var downstrokeStartMs = 0L
    private var peakNegativeAccel = 0f  // strongest downward force this cycle
    private var phaseEnteredMs = 0L
    private var lastActivityMs = 0L

    // Rate tracking
    private var compressionIdx = 0
    private var lastCompressionMs = 0L
    private val compressionTimestamps = mutableListOf<Long>()
    private val recentIntervals = ArrayDeque<Int>(5)

    // Timing
    private var lastTimestamp = 0L

    // Thresholds
    private val downstrokeThreshold = -2.0f   // m/s² — negative = pushing down
    private val recoilThreshold = 0.5f         // m/s² — positive = moving up
    private val velocityNearZero = 0.3f        // m/s — cycle boundary
    private val minCompressionIntervalMs = 150L
    private val idleTimeoutMs = 3000L
    private val maxPhaseDurationMs = 1500L
    private val motionActivityThreshold = 0.8f

    // AHA guidelines
    private val targetRateMin = 100
    private val targetRateMax = 120
    private val targetDepthMinMm = 50f
    private val targetDepthMaxMm = 60f

    fun start() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        reset()
    }

    private fun reset() {
        phase = CyclePhase.IDLE
        velocity = 0f
        displacement = 0f
        minDisplacement = 0f
        downstrokeStartMs = 0L
        peakNegativeAccel = 0f
        phaseEnteredMs = 0L
        lastActivityMs = 0L
        compressionIdx = 0
        lastCompressionMs = 0L
        compressionTimestamps.clear()
        recentIntervals.clear()
        lastTimestamp = 0L
        gravityInitialized = false
        gravityX = 0f; gravityY = 0f; gravityZ = 0f
        calibrationStartMs = 0L
        isCalibrating = true
        _metrics.value = CompressionMetrics()
        _liveSample.value = AccelerometerSample()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val now = event.timestamp
        if (lastTimestamp == 0L) { lastTimestamp = now; return }

        val dt = (now - lastTimestamp) / 1_000_000_000f
        lastTimestamp = now
        if (dt <= 0 || dt > 0.1f) return

        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        // Gravity estimation
        if (!gravityInitialized) {
            gravityX = rawX; gravityY = rawY; gravityZ = rawZ
            gravityInitialized = true
            calibrationStartMs = now / 1_000_000
        }
        val alpha = gravityTimeConstantSec / (gravityTimeConstantSec + dt)
        gravityX = alpha * gravityX + (1 - alpha) * rawX
        gravityY = alpha * gravityY + (1 - alpha) * rawY
        gravityZ = alpha * gravityZ + (1 - alpha) * rawZ

        val currentTimeMs = now / 1_000_000

        // Calibration phase
        if (isCalibrating) {
            if (currentTimeMs - calibrationStartMs < calibrationDurationMs) {
                _metrics.value = CompressionMetrics(feedback = CompressionFeedback.CALIBRATING)
                return
            }
            isCalibrating = false
        }

        // Linear acceleration
        val ax = rawX - gravityX
        val ay = rawY - gravityY
        val az = rawZ - gravityZ

        // Signed vertical acceleration: negative = pushing down, positive = recoiling up
        val gMag = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
        val verticalAccel = if (gMag > 0.1f) {
            (ax * gravityX + ay * gravityY + az * gravityZ) / gMag
        } else {
            az
        }

        val accelMagnitude = sqrt(ax * ax + ay * ay + az * az)

        _liveSample.value = AccelerometerSample(
            x = ax, y = ay, z = az,
            magnitude = accelMagnitude,
            timestampMs = currentTimeMs
        )

        if (accelMagnitude > motionActivityThreshold) {
            lastActivityMs = currentTimeMs
        }

        // State machine for compression cycle detection
        when (phase) {
            CyclePhase.IDLE -> {
                if (verticalAccel < downstrokeThreshold) {
                    val timeSinceLast = currentTimeMs - lastCompressionMs
                    if (lastCompressionMs == 0L || timeSinceLast > minCompressionIntervalMs) {
                        phase = CyclePhase.DOWNSTROKE
                        phaseEnteredMs = currentTimeMs
                        downstrokeStartMs = currentTimeMs
                        velocity = 0f
                        displacement = 0f
                        minDisplacement = 0f
                        peakNegativeAccel = verticalAccel
                    }
                }
            }

            CyclePhase.DOWNSTROKE -> {
                velocity += verticalAccel * dt
                displacement += velocity * dt

                if (displacement < minDisplacement) {
                    minDisplacement = displacement
                }
                if (verticalAccel < peakNegativeAccel) {
                    peakNegativeAccel = verticalAccel
                }

                // Transition to recoil when acceleration goes positive
                if (verticalAccel > recoilThreshold) {
                    phase = CyclePhase.RECOIL
                    phaseEnteredMs = currentTimeMs
                }
            }

            CyclePhase.RECOIL -> {
                velocity += verticalAccel * dt
                displacement += velocity * dt

                // Compression complete when velocity returns near zero
                // (hands momentarily stationary between compressions)
                if (abs(velocity) < velocityNearZero) { //&& displacement > minDisplacement) {
                    onCompressionComplete(currentTimeMs)
                    phase = CyclePhase.IDLE
                    phaseEnteredMs = currentTimeMs
                }
            }
        }

        // Recovery timeouts:
        // 1) Reset if a phase lasts too long without completing.
        // 2) Reset after inactivity based on real motion/compression activity.
        val stuckPhase = phase != CyclePhase.IDLE &&
            phaseEnteredMs > 0L &&
            (currentTimeMs - phaseEnteredMs) > maxPhaseDurationMs
        val inactivitySince = max(lastActivityMs, lastCompressionMs)
        val inactiveTooLong = inactivitySince > 0L && (currentTimeMs - inactivitySince) > idleTimeoutMs

        if (stuckPhase || inactiveTooLong) {
            resetCycleState()
            _metrics.value = CompressionMetrics()
        }
    }

    private fun onCompressionComplete(timestampMs: Long) {
        compressionIdx++
        lastActivityMs = timestampMs

        val intervalMs = if (lastCompressionMs > 0) (timestampMs - lastCompressionMs).toInt() else 545
        lastCompressionMs = timestampMs

        // Rate calculation
        compressionTimestamps.add(timestampMs)
        pruneOldTimestamps(timestampMs)
        recentIntervals.addLast(intervalMs)
        if (recentIntervals.size > 5) recentIntervals.removeFirst()

        val rate = if (compressionTimestamps.size >= 2) {
            val windowSec = (compressionTimestamps.last() - compressionTimestamps.first()) / 1000f
            if (windowSec > 0) ((compressionTimestamps.size - 1) / windowSec * 60).toInt() else 0
        } else 0

        // Depth: minDisplacement is negative (meters downward), convert to positive mm
        val depthMm = (abs(minDisplacement) * 1000f).coerceIn(0f, 100f)

        // Recoil: how far displacement came back from the deepest point
        val recoilMm = ((displacement - minDisplacement) * 1000f).coerceIn(0f, 100f)
        val recoilPct = if (depthMm > 0) (recoilMm / depthMm * 100f).coerceIn(0f, 100f) else 100f

        // Duty cycle: downstroke duration / total interval
        val dutyCycle = if (intervalMs > 0) {
            ((timestampMs - downstrokeStartMs).toFloat() / intervalMs).coerceIn(0f, 1f)
        } else 0.5f

        val depthCm = depthMm / 10f

        val feedback = when {
            rate > 0 && rate < targetRateMin -> CompressionFeedback.TOO_SLOW
            rate > targetRateMax -> CompressionFeedback.TOO_FAST
            depthMm > 0 && depthMm < targetDepthMinMm -> CompressionFeedback.TOO_SHALLOW
            depthMm > targetDepthMaxMm -> CompressionFeedback.TOO_DEEP
            else -> CompressionFeedback.GOOD
        }

        _metrics.value = CompressionMetrics(
            rate = rate,
            depthCm = depthCm,
            isCompressing = true,
            feedback = feedback
        )

        _compressionCompleted.tryEmit(
            CompressionResult(
                compressionIdx = compressionIdx,
                timestampMs = timestampMs,
                intervalMs = intervalMs,
                rate = rate,
                depthMm = depthMm,
                recoilMm = recoilMm,
                recoilPct = recoilPct,
                peakAccel = abs(peakNegativeAccel),
                dutyCycle = dutyCycle
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun resetCycleState() {
        phase = CyclePhase.IDLE
        phaseEnteredMs = 0L
        velocity = 0f
        displacement = 0f
        minDisplacement = 0f
        downstrokeStartMs = 0L
        peakNegativeAccel = 0f
        lastCompressionMs = 0L
        compressionTimestamps.clear()
        recentIntervals.clear()
    }

    private fun pruneOldTimestamps(currentTimeMs: Long) {
        compressionTimestamps.removeAll { currentTimeMs - it > 10_000L }
    }
}
