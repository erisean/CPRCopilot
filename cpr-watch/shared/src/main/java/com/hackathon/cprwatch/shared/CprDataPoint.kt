package com.hackathon.cprwatch.shared

import kotlinx.serialization.Serializable

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
