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

        val avgDepthM = trimmed.map { it.depthMm / 1000f }.average().toFloat()
        val avgPeakAccel = trimmed.map { it.peakAccelMps2 }.average().toFloat()

        // F = m * a, estimate effective mass from target force and measured acceleration
        // k = F / d (spring constant, N/m)
        // For the measured compressions: F_measured ≈ effective_mass * avgPeakAccel
        // We don't know exact mass, so we use ratio approach:
        //   stiffness = avgPeakAccel / avgDepthM  (accel per meter of displacement)
        val stiffness = if (avgDepthM > 0.001f) avgPeakAccel / avgDepthM else 1f

        // Target depth for this surface = targetAccel / stiffness
        // Target accel for CPR: ~50kg * 9.81 / bodyMass ≈ use measured ratio
        // Scale the AHA target (50-60mm on a chest) to this surface
        // On a standard chest: stiffness_chest ≈ typical_peak_accel / 0.055m
        // Ratio: targetDepth_surface = targetDepth_chest * (stiffness_chest / stiffness_surface)
        // Simpler: use the force-depth relationship directly
        //   target depth = target_force / (stiffness * effective_mass)
        // Since stiffness = accel/depth, target_accel maps to target_depth = target_accel / stiffness

        // Reference: on a real chest, ~8-12 m/s² peak accel produces 50-60mm depth
        val refAccelMin = 8f
        val refAccelMax = 12f

        val targetDepthMin = (refAccelMin / stiffness * 1000f).coerceIn(10f, 300f)
        val targetDepthMax = (refAccelMax / stiffness * 1000f).coerceIn(15f, 350f)

        val label = when {
            avgDepthM * 1000f < 30f -> "Firm surface"
            avgDepthM * 1000f < 80f -> "Medium surface"
            else -> "Soft surface"
        }

        _profile = SurfaceProfile(
            stiffness = stiffness,
            targetDepthMinMm = targetDepthMin,
            targetDepthMaxMm = targetDepthMax,
            targetPeakAccelMin = refAccelMin,
            targetPeakAccelMax = refAccelMax,
            surfaceLabel = label
        )

        Log.i(TAG, "Surface calibrated: $label, stiffness=%.1f, targetDepth=%.0f-%.0fmm, avgMeasuredDepth=%.1fmm, avgPeakAccel=%.2f"
            .format(stiffness, targetDepthMin, targetDepthMax, avgDepthM * 1000f, avgPeakAccel))
    }

    fun reset() {
        samples.clear()
        _profile = null
    }

    companion object {
        private const val TAG = "SurfaceCalibrator"
    }
}
