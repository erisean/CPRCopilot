package com.hackathon.cprwatch.mobile

import com.hackathon.cprwatch.shared.CompressionEvent

data class MobileSurfaceProfile(
    val surfaceLabel: String,
    val targetDepthMinMm: Float,
    val targetDepthMaxMm: Float,
    val stiffness: Float
)

class MobileSurfaceCalibrator(private val calibrationCount: Int = 8) {

    private val samples = mutableListOf<Pair<Float, Float>>()
    private var _profile: MobileSurfaceProfile? = null
    val profile: MobileSurfaceProfile? get() = _profile
    val isCalibrated: Boolean get() = _profile != null
    val progress: Float get() = (samples.size.toFloat() / calibrationCount).coerceIn(0f, 1f)
    val compressionsRemaining: Int get() = (calibrationCount - samples.size).coerceAtLeast(0)

    fun addEvent(event: CompressionEvent) {
        if (isCalibrated) return
        if (event.estimatedDepthMm < 1f || event.peakAccelMps2 < 0.5f) return

        samples.add(event.estimatedDepthMm to event.peakAccelMps2)

        if (samples.size >= calibrationCount) {
            computeProfile()
        }
    }

    private fun computeProfile() {
        val trimmed = samples.sortedBy { it.first }.drop(1).dropLast(1)
        val avgDepthMm = trimmed.map { it.first }.average().toFloat()
        val avgPeakAccel = trimmed.map { it.second }.average().toFloat()

        val stiffness = if (avgDepthMm > 1f) avgPeakAccel / (avgDepthMm / 1000f) else 1f

        val targetDepthMin = (avgDepthMm * 0.85f).coerceAtLeast(10f)
        val targetDepthMax = (avgDepthMm * 1.15f).coerceAtLeast(15f)

        val label = when {
            avgDepthMm < 30f -> "Firm surface"
            avgDepthMm < 80f -> "Medium surface"
            else -> "Soft surface"
        }

        _profile = MobileSurfaceProfile(
            surfaceLabel = label,
            targetDepthMinMm = targetDepthMin,
            targetDepthMaxMm = targetDepthMax,
            stiffness = stiffness
        )
    }

    fun reset() {
        samples.clear()
        _profile = null
    }
}
