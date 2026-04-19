package com.hackathon.cprwatch.shared.insights

import java.util.Locale

/**
 * Compact user message for Claude (mobile recap): headline scorecard + condensed diagnostics so the model
 * does not mirror giant tables into the reply.
 */
fun SessionSummary.buildClaudeUserPrompt(scorecard: ScorecardAlignedStats): String {
    val fmt1 = { v: Double -> String.format(Locale.US, "%.1f", v) }
    val fmt0 = { v: Double -> String.format(Locale.US, "%.0f", v) }
    val pctStreak =
        String.format(Locale.US, "%.0f", scorecard.longestStreakFractionOfSession * 100f)
    val rateStd = String.format(Locale.US, "%.1f", scorecard.rollingRateStdBpm)
    return """
**Reply style (mandatory):** **≤5 sentences**, ideally **3–4**; aim **≤50 words**. Plain prose—**no lists**, no stat dump. Sound like a quick hallway debrief. **Optional:** weave in **at most one short clause each** for what to **start**, **continue**, and **stop** doing—only when something below clearly supports it; otherwise skip.

Session metrics:

For **rate performance**, prioritize time-in-zone, wall-time split, streak, and rolling BPM consistency (std dev)—mean rolling BPM alone is **not** a quality score.

## Scorecard

- Grade: ${scorecard.gradeLetter} (${scorecard.gradeLabel})
- Duration ${ScorecardAlignedStats.formatMmSs(scorecard.sessionDurationSec)} (${scorecard.sessionDurationSec}s wall) · ${scorecard.totalCompressions} compressions
- In-zone % (100–120 rolling): ${scorecard.inZonePct}% · Rolling BPM σ: ${rateStd} BPM · Too fast/slow mix: ${scorecard.tooFastPct}% / ${scorecard.tooSlowPct}%
- Wall-time (approx.): in-zone ${scorecard.approxWallTimeInZoneSec}s · fast ${scorecard.approxWallTimeTooFastSec}s · slow ${scorecard.approxWallTimeTooSlowSec}s
- Best in-zone streak: ${scorecard.longestInZoneStreakSec}s (${pctStreak}% of span)
- Mean rolling BPM (context only): ${scorecard.avgRateBpm}

Depth/recoil averages: depth ${fmt1(scorecard.avgDepthMm)} mm · recoil ${fmt0(scorecard.avgRecoilPct)}%

${formatDiagnosticsCompact()}
""".trimIndent()
}

private fun SessionSummary.formatDiagnosticsCompact(): String {
    val instruct = instructionsIssued.entries.joinToString(", ") { "${it.key}×${it.value}" }
        .ifEmpty { "none" }
    val windowsBrief = timeWindows.take(12).joinToString(" · ") { w ->
        "${w.windowLabel}: r≈${w.meanRateBpm.toInt()} d≈${fmt0(w.meanDepthMm)}mm rc≈${fmt0(w.meanRecoilPct)}% ok≈${w.pctQualityGood.toInt()}%"
    }.ifEmpty { "n/a" }
    return """
## Diagnostics (supporting detail only—do not transcribe into your reply)

All-guidelines-met ${pctAllGuidelinesMet}% · CPR fraction ${cprFractionPct}% · Pauses ${pauses.size} · Instant rate in-band ${rate.pctInGuideline}% · Depth in-band ${depth.pctInGuideline}% · Full recoil ≥95% on ${recoil.pctFullRecoil}% · HR ${rescuerHr.start}→${rescuerHr.end} (peak ${rescuerHr.peak})

Instructions counted: $instruct

30s windows: $windowsBrief
""".trimIndent()
}

private fun fmt0(v: Float): String = String.format(Locale.US, "%.0f", v.toDouble())
