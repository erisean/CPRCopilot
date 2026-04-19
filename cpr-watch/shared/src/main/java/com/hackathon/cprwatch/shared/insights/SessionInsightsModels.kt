package com.hackathon.cprwatch.shared.insights

enum class OverallGrade { A, B, C, D, F }

enum class InsightCategory {
    COMPRESSION_RATE,
    COMPRESSION_DEPTH,
    RECOIL,
    CPR_FRACTION,
    FATIGUE,
    CONSISTENCY,
    OVERALL_TECHNIQUE
}

/** Maps API snake_case names from Claude tool output. */
fun parseInsightCategory(raw: String): InsightCategory = when (raw.lowercase()) {
    "compression_rate" -> InsightCategory.COMPRESSION_RATE
    "compression_depth" -> InsightCategory.COMPRESSION_DEPTH
    "recoil" -> InsightCategory.RECOIL
    "cpr_fraction" -> InsightCategory.CPR_FRACTION
    "fatigue" -> InsightCategory.FATIGUE
    "consistency" -> InsightCategory.CONSISTENCY
    "overall_technique" -> InsightCategory.OVERALL_TECHNIQUE
    else -> InsightCategory.OVERALL_TECHNIQUE
}

enum class InsightStatus {
    GOOD,
    WARNING,
    CRITICAL
}

/** One scorecard insight row — primary API for UI (not JSON-first). */
data class InsightItem(
    val category: InsightCategory,
    val title: String,
    val status: InsightStatus,
    val metricValue: String,
    val explanation: String,
    val recommendation: String
)

data class SessionInsights(
    val overallSummary: String,
    val overallGrade: OverallGrade,
    val insights: List<InsightItem>,
    val topPriority: String
)
