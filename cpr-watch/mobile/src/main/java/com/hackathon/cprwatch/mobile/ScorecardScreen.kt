package com.hackathon.cprwatch.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackathon.cprwatch.shared.CprDataPoint
import com.hackathon.cprwatch.shared.CprSession

private val GradeGreen = Color(0xFF6EE7A0)
private val GradeBlue = Color(0xFF85B7EB)
private val GradeAmber = Color(0xFFF5A623)
private val GradeRed = Color(0xFFE24B4A)

private data class GradeInfo(
    val letter: String,
    val color: Color,
    val label: String
)

private fun gradeFor(inZonePct: Int): GradeInfo = when {
    inZonePct >= 80 -> GradeInfo("A", GradeGreen, "Excellent")
    inZonePct >= 65 -> GradeInfo("B", GradeBlue, "Good")
    inZonePct >= 45 -> GradeInfo("C", GradeAmber, "Needs work")
    else -> GradeInfo("D", GradeRed, "Keep practicing")
}

@Composable
fun ScorecardScreen(
    session: CprSession?,
    onDismiss: () -> Unit
) {
    val dataPoints = session?.dataPoints ?: emptyList()
    if (dataPoints.isEmpty()) {
        onDismiss()
        return
    }

    val totalCompressions = dataPoints.size
    val avgRate = dataPoints.map { it.rate }.average().toInt()
    val inZoneCount = dataPoints.count { it.rate in 100..120 }
    val inZonePct = inZoneCount * 100 / totalCompressions
    val tooFastCount = dataPoints.count { it.rate > 120 }
    val tooSlowCount = dataPoints.count { it.rate < 100 }
    val durationSec = if (dataPoints.size >= 2) {
        (dataPoints.last().timestampMs - dataPoints.first().timestampMs) / 1000
    } else 0L
    val bestStreak = computeBestStreak(dataPoints)
    val grade = gradeFor(inZonePct)
    val summary = generateSummary(dataPoints, grade, inZonePct, avgRate, tooFastCount, tooSlowCount, durationSec)
    val tip = generateTip(grade, tooFastCount, tooSlowCount, totalCompressions, dataPoints)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Session Complete",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Grade ring
            GradeRing(grade = grade, inZonePct = inZonePct)

            Spacer(modifier = Modifier.height(12.dp))

            // Summary text
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics grid
            MetricsGrid(
                durationSec = durationSec,
                totalCompressions = totalCompressions,
                avgRate = avgRate,
                bestStreakSec = bestStreak
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Annotated rate chart
            AnnotatedRateChart(dataPoints = dataPoints)

            Spacer(modifier = Modifier.height(20.dp))

            // Time breakdown
            TimeBreakdown(
                inZonePct = inZonePct,
                tooFastPct = if (totalCompressions > 0) tooFastCount * 100 / totalCompressions else 0,
                tooSlowPct = if (totalCompressions > 0) tooSlowCount * 100 / totalCompressions else 0,
                durationSec = durationSec
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Coaching tip
            CoachingTip(tip = tip, grade = grade)

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GradeRing(grade: GradeInfo, inZonePct: Int) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val strokeWidth = 12.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            drawArc(
                color = grade.color.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            drawArc(
                color = grade.color,
                startAngle = -90f,
                sweepAngle = 360f * inZonePct / 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = grade.letter,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = grade.color
            )
            Text(
                text = "$inZonePct% in zone",
                fontSize = 14.sp,
                color = grade.color.copy(alpha = 0.8f)
            )
            Text(
                text = grade.label,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun MetricsGrid(
    durationSec: Long,
    totalCompressions: Int,
    avgRate: Int,
    bestStreakSec: Long
) {
    val rateProgress = if (avgRate in 60..160) {
        ((avgRate - 60).toFloat() / 100f).coerceIn(0f, 1f)
    } else 0f
    val rateInZone = avgRate in 100..120
    val streakProgress = if (durationSec > 0) {
        (bestStreakSec.toFloat() / durationSec).coerceIn(0f, 1f)
    } else 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = "Duration",
                value = formatDuration(durationSec),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Compressions",
                value = "$totalCompressions",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricCard(
                label = "Avg Rate",
                value = "$avgRate BPM",
                progress = rateProgress,
                progressColor = if (rateInZone) GradeGreen else GradeAmber,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                label = "Best Streak",
                value = formatDuration(bestStreakSec),
                progress = streakProgress,
                progressColor = GradeBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    progress: Float? = null,
    progressColor: Color = GradeGreen,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            if (progress != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun AnnotatedRateChart(dataPoints: List<CprDataPoint>) {
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = "Rate Over Time",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 12.dp)
        ) {
            if (dataPoints.size < 2) return@Canvas

            val minValue = 60f
            val maxValue = 160f
            val range = maxValue - minValue
            val bandTopY = size.height * (1 - (120f - minValue) / range)
            val bandBottomY = size.height * (1 - (100f - minValue) / range)

            drawRect(
                color = Color(0x1A6EE7A0),
                topLeft = Offset(0f, bandTopY),
                size = Size(size.width, bandBottomY - bandTopY)
            )
            drawDashedLine(bandTopY, GradeGreen.copy(alpha = 0.3f))
            drawDashedLine(bandBottomY, GradeGreen.copy(alpha = 0.3f))

            val path = Path()
            val xStep = size.width / (dataPoints.size - 1).coerceAtLeast(1)

            dataPoints.forEachIndexed { index, point ->
                val value = point.rate.toFloat().coerceIn(minValue, maxValue)
                val x = index * xStep
                val y = size.height * (1 - (value - minValue) / range)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(path = path, color = Color(0xFF42A5F5), style = Stroke(width = 2.5.dp.toPx()))

            // Find peak spikes to annotate (local maxima that are out of zone)
            val peaks = findPeaks(dataPoints)
            peaks.forEach { peakIdx ->
                val point = dataPoints[peakIdx]
                val value = point.rate.toFloat().coerceIn(minValue, maxValue)
                val x = peakIdx * xStep
                val y = size.height * (1 - (value - minValue) / range)

                drawCircle(
                    color = GradeRed,
                    radius = 5.dp.toPx(),
                    center = Offset(x, y)
                )

                val labelText = "${point.rate}"
                val textResult = textMeasurer.measure(
                    text = labelText,
                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold)
                )
                drawText(
                    textLayoutResult = textResult,
                    color = GradeRed,
                    topLeft = Offset(
                        x - textResult.size.width / 2,
                        y - textResult.size.height - 6.dp.toPx()
                    )
                )
            }
        }
    }
}

@Composable
private fun TimeBreakdown(
    inZonePct: Int,
    tooFastPct: Int,
    tooSlowPct: Int,
    durationSec: Long
) {
    val inZoneSec = durationSec * inZonePct / 100
    val tooFastSec = durationSec * tooFastPct / 100
    val tooSlowSec = durationSec * tooSlowPct / 100

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Time Breakdown",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            BreakdownRow(
                color = GradeGreen,
                label = "In zone (100–120 BPM)",
                time = formatDuration(inZoneSec),
                pct = "$inZonePct%"
            )
            Spacer(modifier = Modifier.height(8.dp))
            BreakdownRow(
                color = GradeRed,
                label = "Too fast (>120 BPM)",
                time = formatDuration(tooFastSec),
                pct = "$tooFastPct%"
            )
            Spacer(modifier = Modifier.height(8.dp))
            BreakdownRow(
                color = GradeAmber,
                label = "Too slow (<100 BPM)",
                time = formatDuration(tooSlowSec),
                pct = "$tooSlowPct%"
            )
        }
    }
}

@Composable
private fun BreakdownRow(
    color: Color,
    label: String,
    time: String,
    pct: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = time,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = pct,
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun CoachingTip(tip: String, grade: GradeInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = grade.color.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Coaching Tip",
                style = MaterialTheme.typography.labelMedium,
                color = grade.color,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tip,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 22.sp
            )
        }
    }
}

// --- Analysis helpers ---

private fun computeBestStreak(dataPoints: List<CprDataPoint>): Long {
    if (dataPoints.size < 2) return 0L
    var bestMs = 0L
    var streakStartMs = dataPoints.first().timestampMs
    var inStreak = dataPoints.first().rate in 100..120

    for (i in 1 until dataPoints.size) {
        val pointInZone = dataPoints[i].rate in 100..120
        if (pointInZone && inStreak) continue
        if (pointInZone && !inStreak) {
            streakStartMs = dataPoints[i].timestampMs
            inStreak = true
        } else if (!pointInZone && inStreak) {
            val streakMs = dataPoints[i].timestampMs - streakStartMs
            if (streakMs > bestMs) bestMs = streakMs
            inStreak = false
        }
    }
    if (inStreak) {
        val streakMs = dataPoints.last().timestampMs - streakStartMs
        if (streakMs > bestMs) bestMs = streakMs
    }
    return bestMs / 1000
}

private fun findPeaks(dataPoints: List<CprDataPoint>): List<Int> {
    if (dataPoints.size < 3) return emptyList()
    val peaks = mutableListOf<Int>()
    for (i in 1 until dataPoints.size - 1) {
        val prev = dataPoints[i - 1].rate
        val curr = dataPoints[i].rate
        val next = dataPoints[i + 1].rate
        if (curr > prev && curr > next && (curr > 120 || curr < 100)) {
            if (peaks.isEmpty() || i - peaks.last() > dataPoints.size / 10) {
                peaks.add(i)
            }
        }
    }
    return peaks.take(5)
}

private fun findLongestPause(dataPoints: List<CprDataPoint>): Long {
    if (dataPoints.size < 2) return 0L
    var maxGapMs = 0L
    for (i in 1 until dataPoints.size) {
        val gap = dataPoints[i].timestampMs - dataPoints[i - 1].timestampMs
        if (gap > maxGapMs) maxGapMs = gap
    }
    return maxGapMs / 1000
}

private fun generateSummary(
    dataPoints: List<CprDataPoint>,
    grade: GradeInfo,
    inZonePct: Int,
    avgRate: Int,
    tooFastCount: Int,
    tooSlowCount: Int,
    durationSec: Long
): String {
    val totalCompressions = dataPoints.size
    val longestPause = findLongestPause(dataPoints)

    val driftInLastThird = if (dataPoints.size > 9) {
        val lastThird = dataPoints.drop(dataPoints.size * 2 / 3)
        val lastThirdAvg = lastThird.map { it.rate }.average().toInt()
        val firstThirdAvg = dataPoints.take(dataPoints.size / 3).map { it.rate }.average().toInt()
        lastThirdAvg - firstThirdAvg
    } else 0

    return when (grade.letter) {
        "A" -> when {
            driftInLastThird > 8 -> "Strong session \u2014 consistent rate with minor drift upward in the final minute."
            driftInLastThird < -8 -> "Strong session \u2014 solid start with a slight slowdown toward the end."
            else -> "Excellent session \u2014 consistent rate and depth throughout."
        }
        "B" -> when {
            tooFastCount > tooSlowCount -> "Good session \u2014 you tend to speed up, especially under fatigue. Focus on matching the metronome."
            driftInLastThird > 10 -> "Good session \u2014 rate drifted faster in the second half. Try to maintain a steady pace."
            else -> "Good session \u2014 mostly on target with some variation. Tighten up consistency for an A."
        }
        "C" -> when {
            tooFastCount > totalCompressions / 2 -> "Needs work \u2014 compressions were mostly too fast. Slow down to match the 110 BPM target."
            tooSlowCount > totalCompressions / 2 -> "Needs work \u2014 compressions were mostly too slow. Pick up the pace to reach 100 BPM."
            longestPause > 5 -> "Needs work \u2014 several pauses longer than 5 seconds interrupted your rhythm."
            else -> "Needs work \u2014 rate was inconsistent. Focus on finding a steady rhythm and maintaining it."
        }
        else -> when {
            tooFastCount > tooSlowCount && longestPause > 5 -> "Keep practicing \u2014 compressions were mostly too fast with several pauses longer than 5 seconds."
            tooFastCount > tooSlowCount -> "Keep practicing \u2014 compressions were too fast. Slow down significantly to reach the 100\u2013120 target."
            tooSlowCount > tooFastCount -> "Keep practicing \u2014 compressions were too slow. Push faster and try to sustain the rhythm."
            else -> "Keep practicing \u2014 rate was highly inconsistent. Focus on a steady, metronome-like rhythm."
        }
    }
}

private fun generateTip(
    grade: GradeInfo,
    tooFastCount: Int,
    tooSlowCount: Int,
    totalCompressions: Int,
    dataPoints: List<CprDataPoint>
): String {
    val longestPause = findLongestPause(dataPoints)
    val hasPauses = longestPause > 3

    return when (grade.letter) {
        "A" -> "Excellent work. To maintain this level, practice with the metronome off occasionally to build internal timing."
        "B" -> when {
            tooFastCount > tooSlowCount -> "You tend to speed up under fatigue. Focus on matching the haptic metronome, especially after the first minute."
            hasPauses -> "Minimize pauses between compressions. If you need to rest, coordinate with a partner for seamless handoffs."
            else -> "You're close to an A. Focus on tightening your rate consistency \u2014 aim for less than 5 BPM variation."
        }
        "C" -> when {
            tooFastCount > totalCompressions / 2 -> "Consciously slow down. Count \u201cone-and-two-and\u201d in your head to approximate 110 BPM. The metronome vibration is your target pace."
            tooSlowCount > totalCompressions / 2 -> "Push harder and faster. Imagine pushing to the beat of \u201cStayin\u2019 Alive\u201d by the Bee Gees \u2014 it\u2019s at 104 BPM."
            else -> "Two areas to focus on: find a consistent rhythm first, then work on maintaining it. Use the metronome as your anchor."
        }
        else -> when {
            tooFastCount > tooSlowCount && hasPauses -> "Two things to focus on: slow down to match the metronome, and avoid pausing between compressions."
            tooFastCount > tooSlowCount -> "You\u2019re compressing too aggressively. Slow down and let the metronome guide your pace \u2014 quality beats speed."
            hasPauses -> "Minimize interruptions. Continuous compressions are critical \u2014 every pause drops blood flow to the brain."
            else -> "Start by just matching the metronome rhythm. Don\u2019t worry about depth yet \u2014 get the timing right first, then add force."
        }
    }
}

private fun DrawScope.drawDashedLine(y: Float, color: Color) {
    val dashWidth = 8.dp.toPx()
    val gapWidth = 4.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = color,
            start = Offset(x, y),
            end = Offset((x + dashWidth).coerceAtMost(size.width), y),
            strokeWidth = 1.dp.toPx()
        )
        x += dashWidth + gapWidth
    }
}
