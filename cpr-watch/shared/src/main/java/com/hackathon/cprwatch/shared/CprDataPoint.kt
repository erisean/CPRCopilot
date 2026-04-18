package com.hackathon.cprwatch.shared

import kotlinx.serialization.Serializable

@Serializable
data class CompressionEvent(
    val compressionIdx: Int,
    val timestampMs: Long,
    val intervalMs: Int,
    val instantaneousRateBpm: Float,
    val rollingRateBpm: Float,
    val estimatedDepthMm: Float,
    val recoilPct: Float,
    val dutyCycle: Float,
    val peakAccelMps2: Float,
    val wristAngleDeg: Float,
    val rescuerHrBpm: Int? = null,
    val isQualityGood: Boolean,
    val instruction: String,
    val instructionPriority: Int? = null
) {
    companion object {
        const val MESSAGE_PATH = "/cpr-compression"
        const val SESSION_START_PATH = "/cpr-session-start"
        const val SESSION_STOP_PATH = "/cpr-session-stop"
    }
}

@Serializable
data class CprDataPoint(
    val timestampMs: Long,
    val rate: Int,
    val depthCm: Float,
    val feedback: String
) {
    companion object {
        const val MESSAGE_PATH = "/cpr-data"
        const val SESSION_START_PATH = "/cpr-session-start"
        const val SESSION_STOP_PATH = "/cpr-session-stop"
    }
}

@Serializable
data class CprSession(
    val startTimeMs: Long,
    val dataPoints: List<CprDataPoint> = emptyList()
)
