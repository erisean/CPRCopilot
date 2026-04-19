package com.hackathon.cprwatch.shared.insights

/** Internal aggregates mirroring data-pipeline `compute_session_summary`. */
data class RateStats(
    val mean: Float,
    val median: Float,
    val std: Float,
    val min: Float,
    val max: Float,
    val pctInGuideline: Float
)

data class DepthStats(
    val meanMm: Float,
    val stdMm: Float,
    val minMm: Float,
    val maxMm: Float,
    val pctInGuideline: Float
)

data class RecoilStats(
    val meanPct: Float,
    val pctFullRecoil: Float
)

data class PauseInfo(
    val afterCompression: Int,
    val durationSec: Float,
    val atTimeSec: Float
)

data class TimeWindowBucket(
    val windowLabel: String,
    val compressions: Int,
    val meanRateBpm: Float,
    val meanDepthMm: Float,
    val meanRecoilPct: Float,
    val pctQualityGood: Float,
    val meanRescuerHr: Int?
)

data class RescuerHrSummary(
    val start: Int,
    val end: Int,
    val peak: Int
)

data class SessionSummary(
    val totalCompressions: Int,
    val durationSec: Float,
    val rate: RateStats,
    val depth: DepthStats,
    val recoil: RecoilStats,
    val cprFractionPct: Float,
    val pauses: List<PauseInfo>,
    val rescuerHr: RescuerHrSummary,
    val timeWindows: List<TimeWindowBucket>,
    val instructionsIssued: Map<String, Int>,
    val pctAllGuidelinesMet: Float
)
