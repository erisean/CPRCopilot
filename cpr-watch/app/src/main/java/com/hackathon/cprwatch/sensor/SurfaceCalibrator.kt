package com.hackathon.cprwatch.sensor

import android.util.Log
import kotlin.math.abs

data class SurfaceProfile(
    val stiffness: Float,
    val targetDepthMinMm: Float,
    val targetDepthMaxMm: Float,
    val targetPeakAccelMin: Float,
    val targetPeakAccelMax: Float,
    val surfaceLabel: String
)

class SurfaceCalibrator(
    private val calibrationCount: Int = 8,
    private val targetForceN: Float = 490f,
    private val targetForceMaxN: Float = 590f
) {
    private val samples = mutableListOf<CalibrationSample>()
    private var _profile: SurfaceProfile? = null
    val profile: SurfaceProfile? get() = _profile
    val isCalibrated: Boolean get() = _profile != null
    val progress: Float get() = (samples.size.toFloat() / calibrationCount).coerceIn(0f, 1f)
    val compressionsRemaining: Int get() = (calibrationCount - samples.size).coerceAtLeast(0)

    private data class CalibrationSample(
        val depthMm: Float,
        val peakAccelMps2: Float
    )

    fun addCompression(result: CompressionResult) {
        if (isCalibrated) return
        if (result.depthMm < 1f || result.peakAccel < 0.5f) return

        samples.add(CalibrationSample(result.depthMm, result.peakAccel))
        Log.d(TAG, "Calibration sample ${samples.size}/$calibrationCount: depth=%.1fmm peakAccel=%.2f m/s²"
            .format(result.depthMm, result.peakAccel))

        if (samples.size >= calibrationCount) {
            computeProfile()
        }
    }

    private fun computeProfile() {
        val trimmed = samples
            .sortedBy { it.depthMm }
            .drop(1)
            .dropLast(1)

        val avgDepthMm = trimmed.map { it.depthMm }.average().toFloat()
        val avgPeakAccel = trimmed.map { it.peakAccelMps2 }.average().toFloat()

        val stiffness = if (avgDepthMm > 1f) avgPeakAccel / (avgDepthMm / 1000f) else 1f

        val targetDepthMin = (avgDepthMm * 0.85f).coerceAtLeast(10f)
        val targetDepthMax = (avgDepthMm * 1.15f).coerceAtLeast(15f)

        val label = when {
            avgDepthMm < 30f -> "Firm surface"
            avgDepthMm < 80f -> "Medium surface"
            else -> "Soft surface"
        }

        _profile = SurfaceProfile(
            stiffness = stiffness,
            targetDepthMinMm = targetDepthMin,
            targetDepthMaxMm = targetDepthMax,
            targetPeakAccelMin = avgPeakAccel * 0.85f,
            targetPeakAccelMax = avgPeakAccel * 1.15f,
            surfaceLabel = label
        )

        Log.i(TAG, "Surface calibrated: $label, targetDepth=%.0f-%.0fmm, avgDepth=%.1fmm, avgPeakAccel=%.2f"
            .format(targetDepthMin, targetDepthMax, avgDepthMm, avgPeakAccel))
    }

    fun reset() {
        samples.clear()
        _profile = null
    }

    companion object {
        private const val TAG = "SurfaceCalibrator"
    }
}
