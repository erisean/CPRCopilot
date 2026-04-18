package com.hackathon.cprwatch.sensor

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

class SignalProcessor(private val sampleRateHz: Float = 50f) {

    private val bandpassFilter = ButterworthBandpass(
        order = 4,
        sampleRate = sampleRateHz,
        lowCutoff = 1.0f,
        highCutoff = 8.0f
    )

    private val gravityAlpha = 0.98f
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 9.81f

    private var orientationInitialized = false

    fun reset() {
        bandpassFilter.reset()
        gravityX = 0f
        gravityY = 0f
        gravityZ = 9.81f
        orientationInitialized = false
    }

    fun processAccel(sample: RawSensorSample): ProcessedSample {
        updateGravityEstimate(sample)

        val verticalAccel = projectOntoGravity(
            sample.accelX, sample.accelY, sample.accelZ
        )

        val filteredAccel = bandpassFilter.filter(verticalAccel)

        val wristAngle = computeWristAngle(sample.accelX, sample.accelY, sample.accelZ)

        return ProcessedSample(
            timestampMs = sample.timestampMs,
            verticalAccel = filteredAccel,
            rawVerticalAccel = verticalAccel,
            wristAngleDeg = wristAngle,
            hrBpm = sample.hrBpm
        )
    }

    private fun updateGravityEstimate(sample: RawSensorSample) {
        if (!orientationInitialized) {
            gravityX = sample.accelX
            gravityY = sample.accelY
            gravityZ = sample.accelZ
            orientationInitialized = true
            return
        }
        gravityX = gravityAlpha * gravityX + (1 - gravityAlpha) * sample.accelX
        gravityY = gravityAlpha * gravityY + (1 - gravityAlpha) * sample.accelY
        gravityZ = gravityAlpha * gravityZ + (1 - gravityAlpha) * sample.accelZ
    }

    private fun projectOntoGravity(ax: Float, ay: Float, az: Float): Float {
        val gMag = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
        if (gMag < 0.1f) return az

        val gxN = gravityX / gMag
        val gyN = gravityY / gMag
        val gzN = gravityZ / gMag

        val userX = ax - gravityX
        val userY = ay - gravityY
        val userZ = az - gravityZ

        return userX * gxN + userY * gyN + userZ * gzN
    }

    private fun computeWristAngle(ax: Float, ay: Float, az: Float): Float {
        val gMag = sqrt(gravityX * gravityX + gravityY * gravityY + gravityZ * gravityZ)
        if (gMag < 0.1f) return 0f

        val dot = (ax * gravityX + ay * gravityY + az * gravityZ) / (gMag * sqrt(ax * ax + ay * ay + az * az + 0.001f))
        return Math.toDegrees(kotlin.math.acos(dot.coerceIn(-1f, 1f)).toDouble()).toFloat()
    }
}

data class ProcessedSample(
    val timestampMs: Long,
    val verticalAccel: Float,
    val rawVerticalAccel: Float,
    val wristAngleDeg: Float,
    val hrBpm: Int? = null
)

class ButterworthBandpass(
    private val order: Int,
    sampleRate: Float,
    lowCutoff: Float,
    highCutoff: Float
) {
    private val sections: List<BiquadSection>

    init {
        val nyquist = sampleRate / 2f
        val wLow = tan(PI.toFloat() * lowCutoff / sampleRate)
        val wHigh = tan(PI.toFloat() * highCutoff / sampleRate)
        val bw = wHigh - wLow
        val w0 = sqrt(wLow * wHigh)

        val numSections = order / 2
        sections = (0 until numSections).flatMap { k ->
            val angle = PI.toFloat() * (2 * k + 1) / (2 * order) + PI.toFloat() / 2
            val realPole = cos(angle)
            val imagPole = sin(angle)

            val a = bw / 2f
            val b = w0
            val d = a * realPole
            val e = a * imagPole

            listOf(
                createBandpassSection(w0, bw, d, e),
                createBandpassSection(w0, bw, d, -e)
            )
        }
    }

    private fun createBandpassSection(w0: Float, bw: Float, d: Float, e: Float): BiquadSection {
        val denom = 1 + bw + w0 * w0
        val b0 = bw / denom
        val b1 = 0f
        val b2 = -bw / denom
        val a1 = (2 * (w0 * w0 - 1)) / denom
        val a2 = (1 - bw + w0 * w0) / denom
        return BiquadSection(b0, b1, b2, a1, a2)
    }

    fun filter(input: Float): Float {
        var value = input
        for (section in sections) {
            value = section.process(value)
        }
        return value
    }

    fun reset() {
        sections.forEach { it.reset() }
    }
}

class BiquadSection(
    private val b0: Float,
    private val b1: Float,
    private val b2: Float,
    private val a1: Float,
    private val a2: Float
) {
    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun process(input: Float): Float {
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = input
        y2 = y1; y1 = output
        return output
    }

    fun reset() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
    }
}
