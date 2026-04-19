package com.hackathon.cprwatch.shared.insights

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Same user prompt shape as [data-pipeline/generate_insights.py] `build_user_prompt`.
 */
fun SessionSummary.buildUserPromptForClaude(): String {
    val pauseLines = if (pauses.isEmpty()) {
        "  - No pauses detected"
    } else {
        pauses.joinToString("\n") { p ->
            "  - At ${p.atTimeSec}s: ${p.durationSec}s pause"
        }
    }

    val windowsJson = formatTimeWindowsJson(timeWindows)

    val instructionsJson = if (instructionsIssued.isEmpty()) {
        "None — all metrics stayed in range"
    } else {
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }.encodeToString(
            buildJsonObject {
                instructionsIssued.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
            }
        )
    }

    return """
Analyze this CPR session and provide performance insights.

## Session Data

**Duration:** ${durationSec}s | **Total compressions:** $totalCompressions | **Overall quality:** $pctAllGuidelinesMet% met all guidelines

### Compression Rate
- Mean: ${rate.mean} BPM (std: ${rate.std})
- Range: ${rate.min} – ${rate.max} BPM
- In guideline (100-120): ${rate.pctInGuideline}%

### Compression Depth
- Mean: ${depth.meanMm} mm (std: ${depth.stdMm})
- Range: ${depth.minMm} – ${depth.maxMm} mm
- In guideline (50-60mm): ${depth.pctInGuideline}%

### Recoil
- Mean: ${recoil.meanPct}%
- Full recoil (>=95%): ${recoil.pctFullRecoil}%

### CPR Fraction
- $cprFractionPct%
- Pauses: ${pauses.size} detected
$pauseLines

### Rescuer Heart Rate
- Start: ${rescuerHr.start} BPM
- End: ${rescuerHr.end} BPM
- Peak: ${rescuerHr.peak} BPM

### Performance Over Time (30-second windows)
$windowsJson

### Real-time Instructions Issued During Session
$instructionsJson
""".trimIndent()
}

private fun formatTimeWindowsJson(windows: List<TimeWindowBucket>): String {
    if (windows.isEmpty()) return "[]"
    val blocks = windows.map { w ->
        val hr = w.meanRescuerHr?.toString() ?: "null"
        """
  {
    "window": "${w.windowLabel}",
    "compressions": ${w.compressions},
    "mean_rate_bpm": ${w.meanRateBpm},
    "mean_depth_mm": ${w.meanDepthMm},
    "mean_recoil_pct": ${w.meanRecoilPct},
    "pct_quality_good": ${w.pctQualityGood},
    "mean_rescuer_hr": $hr
  }""".trimIndent()
    }
    return "[\n${blocks.joinToString(",\n")}\n]"
}
