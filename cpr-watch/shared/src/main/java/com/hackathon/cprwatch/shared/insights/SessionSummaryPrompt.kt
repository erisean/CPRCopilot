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
**Reply style (mandatory):** **≤5 sentences**, ideally **3–4**; aim **≤50 words**. Plain prose—**no lists**, no stat dump. Sound like a quick hallway debrief.
**Rescuer HR (watch) vs compression rate:** when the Scorecard lists rescuer HR samples (**rescuerHrSampleCount > 0**), include **≥1 short phrase** about heart-rate **trend or effort** (early vs late, climb, easing)—plain language, **no BP diagnosis**. Skip HR wording **only** when there were **zero** rescuer readings. Never treat rescuer HR as compression BPM.
**Optional:** weave in **at most one short clause each** for **start / continue / stop**—only when metrics clearly support it.
**Optional:** if rescuer HR trends and **compression quality clearly worsen together**, note that link briefly (coach tone); skip if ambiguous.

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

${formatRescuerHrScorecardLine()}

${formatDiagnosticsCompact()}
""".trimIndent()
}

private fun SessionSummary.formatRescuerHrScorecardLine(): String =
    if (rescuerHrSampleCount == 0) {
        "- **Rescuer HR (watch):** no readings on compressions—do **not** invent HR or fatigue from compression rate alone."
    } else {
        "- **Rescuer HR (watch):** ~${rescuerHr.start}→${rescuerHr.end} BPM (peak ${rescuerHr.peak}) · readings on **$rescuerHrSampleCount/${totalCompressions}** compressions · **not** the same as compression *rate*."
    }

private fun SessionSummary.formatDiagnosticsCompact(): String {
    val instruct = instructionsIssued.entries.joinToString(", ") { "${it.key}×${it.value}" }
        .ifEmpty { "none" }
    val windowsBrief = timeWindows.take(12).joinToString(" · ") { w ->
        val hrPart = w.meanRescuerHr?.let { " hr≈$it" }.orEmpty()
        "${w.windowLabel}: r≈${w.meanRateBpm.toInt()} d≈${fmt0(w.meanDepthMm)}mm rc≈${fmt0(w.meanRecoilPct)}% ok≈${w.pctQualityGood.toInt()}%$hrPart"
    }.ifEmpty { "n/a" }
    return """
## Diagnostics (supporting detail only—do not transcribe into your reply)

All-guidelines-met ${pctAllGuidelinesMet}% · CPR fraction ${cprFractionPct}% · Pauses ${pauses.size} · Instant rate in-band ${rate.pctInGuideline}% · Depth in-band ${depth.pctInGuideline}% · Full recoil ≥95% on ${recoil.pctFullRecoil}% · **Rescuer HR** ${rescuerHr.start}→${rescuerHr.end} (peak ${rescuerHr.peak}) · samples=$rescuerHrSampleCount

Instructions counted: $instruct

30s windows: $windowsBrief

${formatPerformanceHrSeriesBlock()}
""".trimIndent()
}

/** Compact CSV (~≤96 rows): tSec, rolling BPM, depth mm, recoil %, HR or "-". */
private fun SessionSummary.formatPerformanceHrSeriesBlock(): String {
    if (performanceHrSeries.isEmpty()) {
        return """
## Downsampled performance + HR (≤96 bins, ~3 s spacing target)
*(No bins—session too short or no usable span.)*
""".trimIndent()
    }
    val span = durationSec.coerceAtLeast(1e-3f)
    val approxBin = span / performanceHrSeries.size
    val hrBins = performanceHrSeries.count { it.rescuerHrBpm != null }
    val fmt1 = { x: Float -> String.format(Locale.US, "%.1f", x.toDouble()) }
    val lines = performanceHrSeries.joinToString("\n") { p ->
        val hr = p.rescuerHrBpm?.toString() ?: "-"
        "${fmt1(p.tSec)},${fmt1(p.rollingRateBpm)},${fmt1(p.depthMm)},${fmt1(p.recoilPct)},$hr"
    }
    return """
## Downsampled performance + HR (supporting detail only—do **not** quote rows in your reply)

Use **only** for fatigue / drift inference vs sparse HR (**"-"** = no HR in that bin). **~${performanceHrSeries.size}** nonempty bins · ~${String.format(Locale.US, "%.1f", approxBin.toDouble())} s / bin · HR present in **${hrBins}** bins. When **${hrBins}** bins include HR data, your recap should still reflect rescuer HR **somewhere** (see Scorecard)—not only compression metrics.

`t_sec,r_roll,d_mm,rc_pct,hr_bpm`
$lines
""".trimIndent()
}

private fun fmt0(v: Float): String = String.format(Locale.US, "%.0f", v.toDouble())
