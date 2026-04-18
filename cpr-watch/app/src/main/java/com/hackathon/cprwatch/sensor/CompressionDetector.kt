package com.hackathon.cprwatch.sensor

import android.content.Context
import com.hackathon.cprwatch.shared.CompressionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

data class CompressionMetrics(
    val rate: Int = 0,
    val depthCm: Float = 0f,
    val isCompressing: Boolean = false,
    val feedback: CompressionFeedback = CompressionFeedback.IDLE
)

enum class CompressionFeedback {
    IDLE, GOOD, TOO_SLOW, TOO_FAST, TOO_SHALLOW, TOO_DEEP;

    val message: String
        get() = when (this) {
            IDLE -> "Tap to start"
            GOOD -> "Good compressions!"
            TOO_SLOW -> "Push faster"
            TOO_FAST -> "Slow down"
            TOO_SHALLOW -> "Push harder"
            TOO_DEEP -> "Ease up"
        }
}

class CompressionDetector(context: Context) {

    private val rawStream = RawSensorStream(context)
    private val signalProcessor = SignalProcessor()
    private val instructionEngine = InstructionEngine()

    private val _metrics = MutableStateFlow(CompressionMetrics())
    val metrics: StateFlow<CompressionMetrics> = _metrics

    private val _compressionEvents = MutableSharedFlow<CompressionEvent>(extraBufferCapacity = 64)
    val compressionEvents: SharedFlow<CompressionEvent> = _compressionEvents

    // Peak detection state
    private var compressionIdx = 0
    private var lastPeakTimeMs = 0L
    private var isInDownstroke = false
    private var downstrokeStartMs = 0L
    private var peakAccelThisCycle = 0f

    // Depth estimation: double-integration per cycle
    private var velocity = 0f
    private var displacement = 0f
    private var lastSampleMs = 0L

    // Recoil estimation
    private var baselineAccel = 0f
    private var recoilAccelSum = 0f
    private var recoilSampleCount = 0

    // Rolling rate: last 5 intervals
    private val recentIntervals = ArrayDeque<Int>(5)

    // Latest HR
    private var latestHrBpm: Int? = null

    // Idle detection
    private val idleTimeoutMs = 3000L

    // Adaptive peak threshold
    private var peakThreshold = 3.0f
    private val minInterPeakMs = 300L

    private var scope: CoroutineScope? = null

    fun start(coroutineScope: CoroutineScope) {
        scope = coroutineScope
        reset()
        signalProcessor.reset()
        instructionEngine.reset()
        rawStream.start()

        coroutineScope.launch {
            rawStream.samples.collect { sample ->
                processSample(sample)
            }
        }
    }

    fun stop() {
        rawStream.stop()
        reset()
    }

    private fun reset() {
        compressionIdx = 0
        lastPeakTimeMs = 0L
        isInDownstroke = false
        downstrokeStartMs = 0L
        peakAccelThisCycle = 0f
        velocity = 0f
        displacement = 0f
        lastSampleMs = 0L
        baselineAccel = 0f
        recoilAccelSum = 0f
        recoilSampleCount = 0
        recentIntervals.clear()
        latestHrBpm = null
        peakThreshold = 3.0f
        _metrics.value = CompressionMetrics()
    }

    private fun processSample(rawSample: RawSensorSample) {
        val processed = signalProcessor.processAccel(rawSample)
        val accel = processed.verticalAccel
        val now = processed.timestampMs

        if (processed.hrBpm != null) {
            latestHrBpm = processed.hrBpm
        }

        if (lastSampleMs == 0L) {
            lastSampleMs = now
            return
        }

        val dtSec = (now - lastSampleMs) / 1000f
        lastSampleMs = now

        if (dtSec <= 0 || dtSec > 0.1f) return

        // Track peak acceleration in current cycle
        if (abs(accel) > peakAccelThisCycle) {
            peakAccelThisCycle = abs(accel)
        }

        if (accel < -peakThreshold && !isInDownstroke) {
            // Downstroke detected (negative = pushing down against gravity)
            val timeSinceLastPeak = now - lastPeakTimeMs

            if (lastPeakTimeMs == 0L || timeSinceLastPeak > minInterPeakMs) {
                if (lastPeakTimeMs > 0L && timeSinceLastPeak < idleTimeoutMs) {
                    emitCompression(now, timeSinceLastPeak.toInt(), processed.wristAngleDeg)
                }

                isInDownstroke = true
                downstrokeStartMs = now
                velocity = 0f
                displacement = 0f
                lastPeakTimeMs = now
                peakAccelThisCycle = abs(accel)
            }
        }

        // Integrate for depth during downstroke
        if (isInDownstroke) {
            velocity += accel * dtSec
            displacement += velocity * dtSec
        }

        // Detect upstroke / recoil phase
        if (isInDownstroke && accel > peakThreshold * 0.3f) {
            isInDownstroke = false
            recoilAccelSum += accel
            recoilSampleCount++
        }

        // Track recoil during release
        if (!isInDownstroke && accel > 0) {
            recoilAccelSum += accel
            recoilSampleCount++
        }

        // Idle detection
        if (lastPeakTimeMs > 0 && (now - lastPeakTimeMs) > idleTimeoutMs) {
            if (_metrics.value.isCompressing) {
                instructionEngine.checkPause(now)
            }
            _metrics.value = _metrics.value.copy(isCompressing = false, feedback = CompressionFeedback.IDLE)
        }
    }

    private fun emitCompression(timestampMs: Long, intervalMs: Int, wristAngleDeg: Float) {
        compressionIdx++

        val instantRate = 60000f / intervalMs
        recentIntervals.addLast(intervalMs)
        if (recentIntervals.size > 5) recentIntervals.removeFirst()
        val rollingRate = if (recentIntervals.isNotEmpty()) {
            60000f / (recentIntervals.average().toFloat())
        } else instantRate

        // Depth: displacement in meters -> millimeters
        val depthMm = (abs(displacement) * 1000f).coerceIn(0f, 100f)

        // Recoil: ratio of upstroke force to downstroke force (simplified)
        val recoilPct = if (recoilSampleCount > 0 && peakAccelThisCycle > 0) {
            ((recoilAccelSum / recoilSampleCount) / peakAccelThisCycle * 100f).coerceIn(0f, 100f)
        } else 100f

        // Duty cycle: fraction of interval spent in downstroke
        val downstrokeDuration = if (downstrokeStartMs > 0) {
            (timestampMs - downstrokeStartMs).toFloat() / intervalMs
        } else 0.5f
        val dutyCycle = downstrokeDuration.coerceIn(0f, 1f)

        val rateGood = rollingRate in 100f..120f
        val depthGood = depthMm in 50f..60f
        val recoilGood = recoilPct >= 95f
        val isQualityGood = rateGood && depthGood && recoilGood

        val instruction = instructionEngine.evaluate(
            timestampMs = timestampMs,
            rollingRate = rollingRate,
            depthMm = depthMm,
            recoilPct = recoilPct,
            rescuerHr = latestHrBpm,
            isQualityGood = isQualityGood
        )

        val event = CompressionEvent(
            compressionIdx = compressionIdx,
            timestampMs = timestampMs,
            intervalMs = intervalMs,
            instantaneousRateBpm = instantRate,
            rollingRateBpm = rollingRate,
            estimatedDepthMm = depthMm,
            recoilPct = recoilPct,
            dutyCycle = dutyCycle,
            peakAccelMps2 = peakAccelThisCycle,
            wristAngleDeg = wristAngleDeg,
            rescuerHrBpm = latestHrBpm,
            isQualityGood = isQualityGood,
            instruction = instruction.value,
            instructionPriority = instruction.priority
        )

        _compressionEvents.tryEmit(event)

        // Update legacy metrics for UI
        val feedback = when {
            rollingRate < 100 -> CompressionFeedback.TOO_SLOW
            rollingRate > 120 -> CompressionFeedback.TOO_FAST
            depthMm < 50 -> CompressionFeedback.TOO_SHALLOW
            depthMm > 60 -> CompressionFeedback.TOO_DEEP
            else -> CompressionFeedback.GOOD
        }

        _metrics.value = CompressionMetrics(
            rate = rollingRate.toInt(),
            depthCm = depthMm / 10f,
            isCompressing = true,
            feedback = feedback
        )

        // Adapt threshold based on observed peak acceleration
        peakThreshold = (peakAccelThisCycle * 0.4f).coerceIn(2.0f, 8.0f)

        // Reset per-cycle accumulators
        recoilAccelSum = 0f
        recoilSampleCount = 0
        peakAccelThisCycle = 0f
    }
}
