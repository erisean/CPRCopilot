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
)

@Serializable
data class CprDataPoint(
    val timestampMs: Long,
    val rate: Int,
    val depthCm: Float,
    val feedback: String
)

object MessagePaths {
    const val SESSION_START = "/cpr-session-start"
    const val SESSION_STOP = "/cpr-session-stop"
    const val COMPRESSION_EVENT = "/cpr-compression"
    const val DATA_POINT = "/cpr-data"
}

@Serializable
data class CprSession(
    val startTimeMs: Long,
    val dataPoints: List<CprDataPoint> = emptyList(),
    val compressionEvents: List<CompressionEvent> = emptyList()
)
