package com.hackathon.cprwatch.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
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
    val dutyCycle: Float,
    val rescuerHrBpm: Int? = null
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

/** Single accelerometer sample captured during a compression cycle, used for ZUPT post-processing. */
private data class CycleSample(val dt: Float, val vertAccel: Float)

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
    private var minDisplacement = 0f
    private var downstrokeStartMs = 0L
    private var peakNegativeAccel = 0f
    private var phaseEnteredMs = 0L
    private var lastActivityMs = 0L

    // Per-cycle sample buffer for ZUPT post-processing
    private val cycleSamples = mutableListOf<CycleSample>()

    // EMA smoothing for vertical acceleration before integration
    private var smoothedVertAccel = 0f
    private val accelSmoothingAlpha = 0.3f   // lower = smoother; 0.3 is a good noise/lag balance
    private var smoothingInitialized = false

    // Rate tracking
    private var compressionIdx = 0
    private var lastCompressionMs = 0L
    private val compressionTimestamps = mutableListOf<Long>()
    private val recentIntervals = ArrayDeque<Int>(5)

    // Timing
    private var lastTimestamp = 0L

    // Thresholds
    private val downstrokeThreshold = -2.0f
    private val recoilThreshold = 0.5f
    private val velocityNearZero = 0.3f
    private val minCompressionIntervalMs = 150L
    private val idleTimeoutMs = 3000L
    private val maxPhaseDurationMs = 1500L
    private val motionActivityThreshold = 0.8f

    companion object {
        private const val TAG = "CompressionDetector"
    }

    // AHA guidelines (defaults for standard chest/manikin)
    private val targetRateMin = 100
    private val targetRateMax = 120
    private val defaultDepthMinMm = 50f
    private val defaultDepthMaxMm = 60f

    // Surface calibration
    private val _surfaceCalibrator = SurfaceCalibrator()
    val surfaceCalibrator: SurfaceCalibrator get() = _surfaceCalibrator

    private val targetDepthMinMm: Float
        get() = _surfaceCalibrator.profile?.targetDepthMinMm ?: defaultDepthMinMm
    private val targetDepthMaxMm: Float
        get() = _surfaceCalibrator.profile?.targetDepthMaxMm ?: defaultDepthMaxMm

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
        cycleSamples.clear()
        smoothingInitialized = false
        _surfaceCalibrator.reset()
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

        // Gravity estimation — only update when not actively compressing to
        // prevent chest motion from contaminating the gravity vector.
        if (!gravityInitialized) {
            gravityX = rawX; gravityY = rawY; gravityZ = rawZ
            gravityInitialized = true
            calibrationStartMs = now / 1_000_000
        }
        if (phase == CyclePhase.IDLE) {
            val alpha = gravityTimeConstantSec / (gravityTimeConstantSec + dt)
            gravityX = alpha * gravityX + (1 - alpha) * rawX
            gravityY = alpha * gravityY + (1 - alpha) * rawY
            gravityZ = alpha * gravityZ + (1 - alpha) * rawZ
            val mag = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
            if (mag > 0.1f) {
                gravityX = gravityX / mag * 9.81f
                gravityY = gravityY / mag * 9.81f
                gravityZ = gravityZ / mag * 9.81f
            }
            Log.v(TAG, "GRAVITY  gx=%.3f  gy=%.3f  gz=%.3f  gMag=%.3f".format(
                gravityX, gravityY, gravityZ,
                sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
            ))
        }

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

        // Signed vertical acceleration projected onto gravity axis
        val gMag = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
        val rawVerticalAccel = if (gMag > 0.1f) {
            (ax * gravityX + ay * gravityY + az * gravityZ) / gMag
        } else {
            az
        }

        // EMA smoothing — reduces high-frequency noise before double-integration
        if (!smoothingInitialized) {
            smoothedVertAccel = rawVerticalAccel
            smoothingInitialized = true
        } else {
            smoothedVertAccel = accelSmoothingAlpha * rawVerticalAccel + (1f - accelSmoothingAlpha) * smoothedVertAccel
        }
        val verticalAccel = smoothedVertAccel

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
                        Log.d(TAG, "→ DOWNSTROKE  t=${currentTimeMs}ms  vertAccel=%.2f  timeSinceLast=${timeSinceLast}ms  gx=%.3f  gy=%.3f  gz=%.3f".format(
                            verticalAccel, gravityX, gravityY, gravityZ))
                        phase = CyclePhase.DOWNSTROKE
                        phaseEnteredMs = currentTimeMs
                        downstrokeStartMs = currentTimeMs
                        velocity = 0f
                        displacement = 0f
                        minDisplacement = 0f
                        peakNegativeAccel = verticalAccel
                        cycleSamples.clear()
                    }
                }
            }

            CyclePhase.DOWNSTROKE -> {
                cycleSamples.add(CycleSample(dt, verticalAccel))

                velocity += verticalAccel * dt
                displacement += velocity * dt
                if (displacement < minDisplacement) minDisplacement = displacement
                if (verticalAccel < peakNegativeAccel) peakNegativeAccel = verticalAccel

                if (verticalAccel > recoilThreshold) {
                    Log.d(TAG, "→ RECOIL  t=${currentTimeMs}ms  vertAccel=%.2f  vel=%.3f  disp=%.4f  minDisp=%.4f  gx=%.3f  gy=%.3f  gz=%.3f".format(
                        verticalAccel, velocity, displacement, minDisplacement, gravityX, gravityY, gravityZ))
                    phase = CyclePhase.RECOIL
                    phaseEnteredMs = currentTimeMs
                }
            }

            CyclePhase.RECOIL -> {
                cycleSamples.add(CycleSample(dt, verticalAccel))

                velocity += verticalAccel * dt
                displacement += velocity * dt

                if (abs(velocity) < velocityNearZero) {
                    Log.d(TAG, "→ COMPLETE  t=${currentTimeMs}ms  vel=%.3f  disp=%.4f  minDisp=%.4f  gx=%.3f  gy=%.3f  gz=%.3f".format(
                        velocity, displacement, minDisplacement, gravityX, gravityY, gravityZ))
                    onCompressionComplete(currentTimeMs)
                    phase = CyclePhase.IDLE
                    phaseEnteredMs = currentTimeMs
                }
            }
        }

        val stuckPhase = phase != CyclePhase.IDLE &&
            phaseEnteredMs > 0L &&
            (currentTimeMs - phaseEnteredMs) > maxPhaseDurationMs
        val inactivitySince = max(lastActivityMs, lastCompressionMs)
        val inactiveTooLong = inactivitySince > 0L && (currentTimeMs - inactivitySince) > idleTimeoutMs

        if (stuckPhase || inactiveTooLong) {
            Log.w(TAG, "RESET  stuck=${stuckPhase}  inactive=${inactiveTooLong}  phase=${phase}  phaseAge=${currentTimeMs - phaseEnteredMs}ms  sinceActivity=${currentTimeMs - lastActivityMs}ms  gx=%.3f  gy=%.3f  gz=%.3f".format(
                gravityX, gravityY, gravityZ))
            resetCycleState()
            _metrics.value = CompressionMetrics()
        }
    }

    /**
     * ZUPT (Zero-Velocity Update) drift correction.
     *
     * Since we know velocity = 0 at the very start of a downstroke and is nominally
     * ~0 at the end of recoil (hands momentarily still between compressions), we can
     * remove the linear drift that accumulates during double-integration:
     *
     *  1. Forward-integrate raw vertical acceleration → uncorrected velocity curve.
     *  2. The final velocity value is the drift error accumulated over the whole cycle.
     *  3. Subtract a linearly-ramped correction so v_corrected(0) = 0 and v_corrected(end) = 0.
     *  4. Integrate the corrected velocity → drift-free displacement curve.
     *  5. The minimum of that displacement is the true compression depth.
     *
     * This eliminates low-frequency bias in the accelerometer that would otherwise
     * cause the naïve double-integral to wander by tens of millimetres per second.
     */
    private fun computeZuptDepthMm(): Float {
        val n = cycleSamples.size
        if (n < 2) return (abs(minDisplacement) * 1000f).coerceIn(0f, 100f)

        // Step 1 – build uncorrected velocity array (size n+1, index 0 = start)
        val vel = FloatArray(n + 1)
        vel[0] = 0f
        for (i in 0 until n) {
            vel[i + 1] = vel[i] + cycleSamples[i].vertAccel * cycleSamples[i].dt
        }

        // Step 2 – linear drift: vel[n] should be 0 at cycle end
        val vDrift = vel[n]

        // Step 3 – corrected velocity (subtract linearly ramped drift)
        val velCorrected = FloatArray(n + 1)
        for (i in 0..n) {
            velCorrected[i] = vel[i] - vDrift * i.toFloat() / n
        }

        // Step 4 – integrate corrected velocity → displacement
        var disp = 0f
        var minDisp = 0f
        for (i in 0 until n) {
            disp += velCorrected[i] * cycleSamples[i].dt
            if (disp < minDisp) minDisp = disp
        }

        val depthMm = (abs(minDisp) * 1000f).coerceIn(0f, 100f)
        Log.d(TAG, "ZUPT  rawMinDisp=%.4f  correctedMinDisp=%.4f  depthMm=%.1f  vDrift=%.4f  samples=$n".format(
            minDisplacement, minDisp, depthMm, vDrift))
        return depthMm
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

        // Use ZUPT-corrected depth instead of raw double-integration result
        val depthMm = computeZuptDepthMm()

        Log.i(TAG, "COMPRESSION #${compressionIdx}  interval=${intervalMs}ms  rate=${rate}bpm  depth=%.1fmm  rawMinDisp=%.4f  gx=%.3f  gy=%.3f  gz=%.3f".format(
            depthMm, minDisplacement, gravityX, gravityY, gravityZ))

        // Recoil: how far displacement came back from the deepest point (use raw displacement for recoil estimate)
        val recoilMm = ((displacement - minDisplacement) * 1000f).coerceIn(0f, 100f)
        val recoilPct = if (depthMm > 0) (recoilMm / depthMm * 100f).coerceIn(0f, 100f) else 100f

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

        val result = CompressionResult(
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

        _surfaceCalibrator.addCompression(result)
        _compressionCompleted.tryEmit(result)
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
        cycleSamples.clear()
    }

    private fun pruneOldTimestamps(currentTimeMs: Long) {
        compressionTimestamps.removeAll { currentTimeMs - it > 3_000L }
    }
}
