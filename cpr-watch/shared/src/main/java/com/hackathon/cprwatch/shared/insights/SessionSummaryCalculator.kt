package com.hackathon.cprwatch.shared.insights

import com.hackathon.cprwatch.shared.CompressionEvent
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Ports `compute_session_summary` from [data-pipeline/generate_insights.py].
 */
object SessionSummaryCalculator {

    /** Cap for downsampled performance + HR series sent to Claude (~60–120 range). */
    private val seriesMaxPoints = 96
    /** Target bin width ≈ this many seconds (actual width adapts to span and [seriesMaxPoints]). */
    private val seriesTargetBinSec = 3f

    fun fromCompressionEvents(events: List<CompressionEvent>): SessionSummary? {
        if (events.isEmpty()) return null

        val n = events.size
        val durationSec = round1((events.last().timestampMs - events.first().timestampMs) / 1000f)
        val rates = FloatArray(n) { events[it].instantaneousRateBpm }
        val depths = FloatArray(n) { events[it].estimatedDepthMm }
        val recoils = FloatArray(n) { events[it].recoilPct }

        val rateStats = RateStats(
            mean = round1(rates.average().toFloat()),
            median = round1(median(rates)),
            std = round1(stdPopulation(rates)),
            min = round1(rates.minOrNull() ?: 0f),
            max = round1(rates.maxOrNull() ?: 0f),
            pctInGuideline = round1(pctTrue(n) { i ->
                rates[i] in 100f..120f
            })
        )

        val depthStats = DepthStats(
            meanMm = round1(depths.average().toFloat()),
            stdMm = round1(stdPopulation(depths)),
            minMm = round1(depths.minOrNull() ?: 0f),
            maxMm = round1(depths.maxOrNull() ?: 0f),
            pctInGuideline = round1(pctTrue(n) { i ->
                depths[i] in 50f..60f
            })
        )

        val recoilStats = RecoilStats(
            meanPct = round1(recoils.average().toFloat()),
            pctFullRecoil = round1(pctTrue(n) { i -> recoils[i] >= 95f })
        )

        val pauses = buildPauses(events)

        val totalPauseTime = pauses.sumOf { it.durationSec.toDouble() }.toFloat()
        val cprFractionPct = if (durationSec > 0f) {
            round1((durationSec - totalPauseTime) / durationSec * 100f)
        } else {
            100f
        }

        val hrValues = events.mapNotNull { it.rescuerHrBpm?.takeIf { h -> h > 0 } }
        val rescuerHrSampleCount = hrValues.size
        val rescuerHr = RescuerHrSummary(
            start = hrValues.take(5).averageOrZero().roundToInt(),
            end = hrValues.takeLast(5).averageOrZero().roundToInt(),
            peak = hrValues.maxOrNull() ?: 0
        )

        val timeWindows = buildTimeWindows(events, durationSec)

        val instructionsIssued = events
            .filter { it.instruction != "none" }
            .groupingBy { it.instruction }
            .eachCount()

        val pctAll = round1(
            pctTrue(n) { i -> events[i].isQualityGood }
        )

        val performanceHrSeries = buildPerformanceHrSeries(events, durationSec)

        return SessionSummary(
            totalCompressions = n,
            rescuerHrSampleCount = rescuerHrSampleCount,
            durationSec = durationSec,
            rate = rateStats,
            depth = depthStats,
            recoil = recoilStats,
            cprFractionPct = cprFractionPct,
            pauses = pauses,
            rescuerHr = rescuerHr,
            timeWindows = timeWindows,
            instructionsIssued = instructionsIssued,
            pctAllGuidelinesMet = pctAll,
            performanceHrSeries = performanceHrSeries,
        )
    }

    /**
     * Uniform time bins (~[seriesTargetBinSec]s spacing when session is long enough), at most [seriesMaxPoints]
     * rows. Empty bins are omitted. Values are bin means (rolling BPM, depth, recoil); HR is mean where present.
     */
    private fun buildPerformanceHrSeries(
        events: List<CompressionEvent>,
        spanSec: Float,
    ): List<PerformanceHrSeriesPoint> {
        if (events.isEmpty()) return emptyList()
        val t0 = events.first().timestampMs
        val span = spanSec.coerceAtLeast(0f)
        val numBinsTarget = ceil(span.toDouble() / seriesTargetBinSec.toDouble()).toInt().coerceAtLeast(1)
        val numBins = numBinsTarget.coerceAtMost(seriesMaxPoints)
        val binSec = if (numBins <= 0) span else span / numBins

        val out = ArrayList<PerformanceHrSeriesPoint>(numBins)
        for (i in 0 until numBins) {
            val relStart = i * binSec
            val relEnd = if (i == numBins - 1) span else (i + 1) * binSec
            val slice = events.filter {
                val rel = (it.timestampMs - t0) / 1000f
                if (i == numBins - 1) rel >= relStart - 1e-4f && rel <= relEnd + 1e-3f
                else rel >= relStart && rel < relEnd
            }
            if (slice.isEmpty()) continue

            val meanRoll = round1(meanFloats(slice.map { it.rollingRateBpm }))
            val meanDepth = round1(meanFloats(slice.map { it.estimatedDepthMm }))
            val meanRecoil = round1(meanFloats(slice.map { it.recoilPct }))
            val hrs = slice.mapNotNull { it.rescuerHrBpm?.takeIf { h -> h > 0 } }
            val meanHr = if (hrs.isEmpty()) null else (hrs.sum().toDouble() / hrs.size).roundToInt()

            val centerT = round1((relStart + relEnd) / 2f)
            out.add(
                PerformanceHrSeriesPoint(
                    tSec = centerT,
                    rollingRateBpm = meanRoll,
                    depthMm = meanDepth,
                    recoilPct = meanRecoil,
                    rescuerHrBpm = meanHr,
                )
            )
        }
        return out
    }

    private fun buildPauses(events: List<CompressionEvent>): List<PauseInfo> {
        val out = mutableListOf<PauseInfo>()
        for (i in 1 until events.size) {
            val gap = events[i].timestampMs - events[i - 1].timestampMs
            if (gap > 2000) {
                out.add(
                    PauseInfo(
                        afterCompression = i + 1,
                        durationSec = round1(gap / 1000f),
                        atTimeSec = round1(events[i].timestampMs / 1000f)
                    )
                )
            }
        }
        return out
    }

    private fun buildTimeWindows(
        events: List<CompressionEvent>,
        durationSec: Float
    ): List<TimeWindowBucket> {
        val out = mutableListOf<TimeWindowBucket>()
        val maxStart = durationSec.toInt() + 1
        var wStart = 0
        while (wStart <= maxStart) {
            val wEnd = wStart + 30
            val slice = events.filter {
                it.timestampMs >= wStart * 1000L && it.timestampMs < wEnd * 1000L
            }
            if (slice.isNotEmpty()) {
                val meanHr = slice.mapNotNull { it.rescuerHrBpm }
                out.add(
                    TimeWindowBucket(
                        windowLabel = "${wStart}-${wEnd}s",
                        compressions = slice.size,
                        meanRateBpm = round1(meanFloats(slice.map { it.instantaneousRateBpm })),
                        meanDepthMm = round1(meanFloats(slice.map { it.estimatedDepthMm })),
                        meanRecoilPct = round1(meanFloats(slice.map { it.recoilPct })),
                        pctQualityGood = round1(
                            slice.count { it.isQualityGood }.toFloat() / slice.size * 100f
                        ),
                        meanRescuerHr = meanHr.takeIf { it.isNotEmpty() }
                            ?.let { xs -> (xs.sum().toDouble() / xs.size).roundToInt() }
                    )
                )
            }
            wStart += 30
        }
        return out
    }

    private fun pctTrue(n: Int, predicate: (Int) -> Boolean): Float =
        if (n == 0) 0f else (0 until n).count(predicate).toFloat() / n * 100f

    private fun median(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sortedArray()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }

    private fun stdPopulation(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val m = values.average().toFloat()
        var sum = 0.0
        for (v in values) {
            val d = v - m
            sum += (d * d).toDouble()
        }
        return sqrt(sum / values.size).toFloat()
    }

    private fun List<Int>.averageOrZero(): Float =
        if (isEmpty()) 0f else sum().toFloat() / size

    private fun round1(x: Float): Float =
        (kotlin.math.round(x.toDouble() * 10.0) / 10.0).toFloat()

    private fun meanFloats(values: List<Float>): Float =
        if (values.isEmpty()) 0f else (values.sumOf { it.toDouble() } / values.size).toFloat()
}
