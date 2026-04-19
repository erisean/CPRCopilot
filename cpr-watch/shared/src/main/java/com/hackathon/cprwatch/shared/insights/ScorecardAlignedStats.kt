package com.hackathon.cprwatch.shared.insights

import com.hackathon.cprwatch.shared.CompressionEvent
import java.util.Locale
import kotlin.math.sqrt

/**
 * Single source for scorecard headline numbers: build once from [CompressionEvent]s
 * (UI + Claude user prompt both consume the same instance).
 * Rate in-zone uses **rolling** BPM (matches hero ring and charts).
 */
data class ScorecardAlignedStats(
    val gradeLetter: String,
    val gradeLabel: String,
    val inZonePct: Int,
    val totalCompressions: Int,
    val sessionDurationSec: Long,
    /** Population std dev of rolling BPM across compressions (consistency). */
    val rollingRateStdBpm: Float,
    val avgRateBpm: Int,
    val tooFastCount: Int,
    val tooSlowCount: Int,
    val longestInZoneStreakSec: Long,
    /** Same progress bar as Metrics grid: streak length / session span (0–1). */
    val longestStreakFractionOfSession: Float,
    val tooFastPct: Int,
    val tooSlowPct: Int,
    /** Same formula as scorecard "Time breakdown" rows (% of compressions × session span). */
    val approxWallTimeInZoneSec: Long,
    val approxWallTimeTooFastSec: Long,
    val approxWallTimeTooSlowSec: Long,
    val avgDepthMm: Double,
    val avgRecoilPct: Double,
) {
    companion object {
        fun from(events: List<CompressionEvent>): ScorecardAlignedStats? {
            if (events.isEmpty()) return null
            val total = events.size
            val inZoneCount = events.count { it.rollingRateBpm in 100f..120f }
            val inZonePct = inZoneCount * 100 / total
            val tooFastCount = events.count { it.rollingRateBpm > 120f }
            val tooSlowCount = events.count { it.rollingRateBpm < 100f }
            val tooFastPct = tooFastCount * 100 / total
            val tooSlowPct = tooSlowCount * 100 / total
            val rollingRates = events.map { it.rollingRateBpm }
            val rateStd = populationStdRollingBpm(rollingRates)
            val avgRate = rollingRates.average().toInt()
            val durationSec = if (events.size >= 2) {
                (events.last().timestampMs - events.first().timestampMs) / 1000
            } else {
                0L
            }
            val bestStreak = computeLongestInZoneStreakSec(events)
            val streakFrac =
                if (durationSec > 0) (bestStreak.toFloat() / durationSec).coerceIn(0f, 1f) else 0f
            val wallIn = durationSec * inZonePct / 100
            val wallFast = durationSec * tooFastPct / 100
            val wallSlow = durationSec * tooSlowPct / 100
            val avgDepth = events.map { it.estimatedDepthMm.toDouble() }.average()
            val avgRecoil = events.map { it.recoilPct.toDouble() }.average()
            val (letter, label) = gradeLabelForInZonePct(inZonePct)
            return ScorecardAlignedStats(
                gradeLetter = letter,
                gradeLabel = label,
                inZonePct = inZonePct,
                totalCompressions = total,
                sessionDurationSec = durationSec,
                rollingRateStdBpm = rateStd,
                avgRateBpm = avgRate,
                tooFastCount = tooFastCount,
                tooSlowCount = tooSlowCount,
                longestInZoneStreakSec = bestStreak,
                longestStreakFractionOfSession = streakFrac,
                tooFastPct = tooFastPct,
                tooSlowPct = tooSlowPct,
                approxWallTimeInZoneSec = wallIn,
                approxWallTimeTooFastSec = wallFast,
                approxWallTimeTooSlowSec = wallSlow,
                avgDepthMm = avgDepth,
                avgRecoilPct = avgRecoil,
            )
        }

        fun formatMmSs(totalSec: Long): String {
            val m = totalSec / 60
            val s = totalSec % 60
            return String.format(Locale.US, "%d:%02d", m, s)
        }

        private fun populationStdRollingBpm(samples: List<Float>): Float {
            if (samples.isEmpty()) return 0f
            val mean = samples.average()
            val variance = samples.sumOf { (it - mean).toDouble().let { d -> d * d } } / samples.size
            return sqrt(variance).toFloat()
        }

        private fun gradeLabelForInZonePct(inZonePct: Int): Pair<String, String> = when {
            inZonePct >= 80 -> "A" to "Excellent"
            inZonePct >= 65 -> "B" to "Good"
            inZonePct >= 45 -> "C" to "Needs work"
            else -> "D" to "Keep practicing"
        }

        private fun computeLongestInZoneStreakSec(events: List<CompressionEvent>): Long {
            if (events.size < 2) return 0L
            var bestMs = 0L
            var streakStart = events.first().timestampMs
            var inStreak = events.first().rollingRateBpm in 100f..120f
            for (i in 1 until events.size) {
                val inZone = events[i].rollingRateBpm in 100f..120f
                when {
                    inZone && !inStreak -> {
                        streakStart = events[i].timestampMs
                        inStreak = true
                    }
                    !inZone && inStreak -> {
                        bestMs = maxOf(bestMs, events[i].timestampMs - streakStart)
                        inStreak = false
                    }
                }
            }
            if (inStreak) {
                bestMs = maxOf(bestMs, events.last().timestampMs - streakStart)
            }
            return bestMs / 1000
        }
    }
}
