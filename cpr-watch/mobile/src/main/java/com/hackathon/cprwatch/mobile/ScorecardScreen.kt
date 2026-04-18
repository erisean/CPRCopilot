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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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

private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)
private val DimText = Color(0xFF6B6B6B)
private val GradeGreen = Color(0xFF6EE7A0)
private val GradeBlue = Color(0xFF85B7EB)
private val GradeAmber = Color(0xFFF5A623)
private val GradeRed = Color(0xFFE24B4A)

private data class GradeInfo(val letter: String, val color: Color, val label: String)

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
    if (dataPoints.isEmpty()) { onDismiss(); return }

    val total = dataPoints.size
    val avgRate = dataPoints.map { it.rate }.average().toInt()
    val inZoneCount = dataPoints.count { it.rate in 100..120 }
    val inZonePct = inZoneCount * 100 / total
    val tooFastCount = dataPoints.count { it.rate > 120 }
    val tooSlowCount = dataPoints.count { it.rate < 100 }
    val tooFastPct = tooFastCount * 100 / total
    val tooSlowPct = tooSlowCount * 100 / total
    val durationSec = if (dataPoints.size >= 2)
        (dataPoints.last().timestampMs - dataPoints.first().timestampMs) / 1000 else 0L
    val bestStreak = computeBestStreak(dataPoints)
    val grade = gradeFor(inZonePct)
    val summary = generateSummary(dataPoints, grade, inZonePct, avgRate, tooFastCount, tooSlowCount, durationSec)

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session review", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Box(
                    modifier = Modifier
                        .background(CardBg, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Completed", fontSize = 12.sp, color = DimText)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Grade ring
            GradeRing(grade = grade, inZonePct = inZonePct)

            Spacer(modifier = Modifier.height(16.dp))

            // Summary
            Text(
                text = summary,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Metrics grid
            MetricsGrid(durationSec, total, avgRate, bestStreak, durationSec)

            Spacer(modifier = Modifier.height(24.dp))

            // Rate over time chart
            Text("Rate over time", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            AnnotatedRateChart(dataPoints = dataPoints, durationSec = durationSec)

            Spacer(modifier = Modifier.height(24.dp))

            // Time breakdown
            Text("Time breakdown", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(12.dp))
            TimeBreakdown(inZonePct, tooFastPct, tooSlowPct, durationSec)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun GradeRing(grade: GradeInfo, inZonePct: Int) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            drawArc(grade.color.copy(alpha = 0.12f), -90f, 360f, false, topLeft, arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round))
            drawArc(grade.color, -90f, 360f * inZonePct / 100f, false, topLeft, arcSize,
                style = Stroke(strokeWidth, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(grade.letter, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = grade.color)
            Text("$inZonePct% in zone", fontSize = 13.sp, color = grade.color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun MetricsGrid(durationSec: Long, total: Int, avgRate: Int, bestStreak: Long, sessionDuration: Long) {
    val streakProgress = if (sessionDuration > 0) (bestStreak.toFloat() / sessionDuration).coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("DURATION", formatDuration(durationSec), modifier = Modifier.weight(1f))
            MetricCard("COMPRESSIONS", "$total", modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("AVG RATE", "$avgRate", subtitle = "target: 100–120 BPM", modifier = Modifier.weight(1f))
            MetricCard(
                "LONGEST STREAK", formatDuration(bestStreak),
                subtitle = "in target zone",
                progress = streakProgress, progressColor = GradeBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    subtitle: String? = null,
    progress: Float? = null,
    progressColor: Color = GradeGreen,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, fontSize = 10.sp, color = DimText, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (subtitle != null) {
                Text(subtitle, fontSize = 11.sp, color = DimText)
            }
            if (progress != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = progressColor,
                    trackColor = progressColor.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun AnnotatedRateChart(dataPoints: List<CprDataPoint>, durationSec: Long) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        if (dataPoints.size < 2) return@Canvas

        val minValue = 80f; val maxValue = 140f; val range = maxValue - minValue
        val bandTopY = size.height * (1 - (120f - minValue) / range)
        val bandBottomY = size.height * (1 - (100f - minValue) / range)

        // Target zone
        drawRect(Color(0x1A6EE7A0), Offset(0f, bandTopY), Size(size.width, bandBottomY - bandTopY))

        // Band labels
        val label120 = textMeasurer.measure("120", TextStyle(fontSize = 9.sp, color = DimText))
        drawText(label120, DimText, Offset(-2f, bandTopY - label120.size.height / 2))
        val label100 = textMeasurer.measure("100", TextStyle(fontSize = 9.sp, color = DimText))
        drawText(label100, DimText, Offset(-2f, bandBottomY - label100.size.height / 2))

        // Dashed band lines
        drawDashedLine(bandTopY, GradeGreen.copy(alpha = 0.25f))
        drawDashedLine(bandBottomY, GradeGreen.copy(alpha = 0.25f))

        // Data line
        val path = Path()
        val xStep = size.width / (dataPoints.size - 1).coerceAtLeast(1)
        dataPoints.forEachIndexed { i, p ->
            val v = p.rate.toFloat().coerceIn(minValue, maxValue)
            val x = i * xStep; val y = size.height * (1 - (v - minValue) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF4CAF50), style = Stroke(2.5.dp.toPx()))

        // Peak annotations
        val peaks = findPeaks(dataPoints)
        peaks.forEach { idx ->
            val p = dataPoints[idx]
            val v = p.rate.toFloat().coerceIn(minValue, maxValue)
            val x = idx * xStep; val y = size.height * (1 - (v - minValue) / range)
            drawCircle(GradeRed, 4.dp.toPx(), Offset(x, y))
            val t = textMeasurer.measure("${p.rate}", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold))
            drawText(t, GradeRed, Offset(x - t.size.width / 2, y - t.size.height - 4.dp.toPx()))
        }

        // Time axis labels
        if (durationSec > 0) {
            val intervals = when {
                durationSec > 180 -> 60L
                durationSec > 60 -> 30L
                else -> 15L
            }
            var tick = intervals
            while (tick < durationSec) {
                val frac = tick.toFloat() / durationSec
                val x = frac * size.width
                val label = textMeasurer.measure(
                    "${tick / 60}:${"%02d".format(tick % 60)}",
                    TextStyle(fontSize = 9.sp)
                )
                drawText(label, DimText, Offset(x - label.size.width / 2, size.height + 4.dp.toPx()))
                tick += intervals
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TimeBreakdown(inZonePct: Int, tooFastPct: Int, tooSlowPct: Int, durationSec: Long) {
    val inZoneSec = durationSec * inZonePct / 100
    val tooFastSec = durationSec * tooFastPct / 100
    val tooSlowSec = durationSec * tooSlowPct / 100

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BreakdownRow(GradeGreen, "In zone (100–120)", formatDuration(inZoneSec), "$inZonePct%")
        BreakdownRow(GradeRed, "Too fast (>120)", formatDuration(tooFastSec), "$tooFastPct%")
        BreakdownRow(GradeAmber, "Too slow (<100)", formatDuration(tooSlowSec), "$tooSlowPct%")
    }
}

@Composable
private fun BreakdownRow(color: Color, label: String, time: String, pct: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
        Text(time, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(end = 12.dp))
        Text(pct, fontSize = 13.sp, color = DimText, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
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
        if (pointInZone && !inStreak) { streakStartMs = dataPoints[i].timestampMs; inStreak = true }
        else if (!pointInZone && inStreak) {
            bestMs = maxOf(bestMs, dataPoints[i].timestampMs - streakStartMs); inStreak = false
        }
    }
    if (inStreak) bestMs = maxOf(bestMs, dataPoints.last().timestampMs - streakStartMs)
    return bestMs / 1000
}

private fun findPeaks(dataPoints: List<CprDataPoint>): List<Int> {
    if (dataPoints.size < 3) return emptyList()
    val peaks = mutableListOf<Int>()
    for (i in 1 until dataPoints.size - 1) {
        val curr = dataPoints[i].rate
        if (curr > dataPoints[i - 1].rate && curr > dataPoints[i + 1].rate && (curr > 120 || curr < 100)) {
            if (peaks.isEmpty() || i - peaks.last() > dataPoints.size / 10) peaks.add(i)
        }
    }
    return peaks.take(5)
}

private fun findLongestPause(dataPoints: List<CprDataPoint>): Long {
    if (dataPoints.size < 2) return 0L
    var maxGap = 0L
    for (i in 1 until dataPoints.size) maxGap = maxOf(maxGap, dataPoints[i].timestampMs - dataPoints[i - 1].timestampMs)
    return maxGap / 1000
}

private fun generateSummary(
    dataPoints: List<CprDataPoint>, grade: GradeInfo, inZonePct: Int, avgRate: Int,
    tooFastCount: Int, tooSlowCount: Int, durationSec: Long
): String {
    val total = dataPoints.size
    val longestPause = findLongestPause(dataPoints)
    val drift = if (dataPoints.size > 9) {
        val third = dataPoints.size / 3
        dataPoints.takeLast(third).map { it.rate }.average().toInt() -
            dataPoints.take(third).map { it.rate }.average().toInt()
    } else 0

    return when (grade.letter) {
        "A" -> when {
            drift > 8 -> "Strong session \u2014 consistent rate with minor drift in the final minute"
            drift < -8 -> "Strong session \u2014 solid start with a slight slowdown toward the end"
            else -> "Excellent session \u2014 consistent rate and depth throughout"
        }
        "B" -> when {
            tooFastCount > tooSlowCount -> "Good session \u2014 you tend to speed up under fatigue. Focus on matching the metronome."
            drift > 10 -> "Good session \u2014 rate drifted faster in the second half."
            else -> "Good session \u2014 mostly on target with some variation."
        }
        "C" -> when {
            tooFastCount > total / 2 -> "Needs work \u2014 compressions were mostly too fast."
            tooSlowCount > total / 2 -> "Needs work \u2014 compressions were mostly too slow."
            longestPause > 5 -> "Needs work \u2014 several pauses interrupted your rhythm."
            else -> "Needs work \u2014 rate was inconsistent."
        }
        else -> when {
            tooFastCount > tooSlowCount && longestPause > 5 -> "Keep practicing \u2014 compressions were too fast with pauses."
            tooFastCount > tooSlowCount -> "Keep practicing \u2014 compressions were too fast."
            tooSlowCount > tooFastCount -> "Keep practicing \u2014 compressions were too slow."
            else -> "Keep practicing \u2014 focus on a steady rhythm."
        }
    }
}

private fun DrawScope.drawDashedLine(y: Float, color: Color) {
    val dash = 6.dp.toPx(); val gap = 4.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, y), Offset((x + dash).coerceAtMost(size.width), y), 1.dp.toPx())
        x += dash + gap
    }
}
