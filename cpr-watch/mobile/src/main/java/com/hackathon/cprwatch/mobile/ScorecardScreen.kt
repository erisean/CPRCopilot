package com.hackathon.cprwatch.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.hackathon.cprwatch.shared.CompressionEvent
import com.hackathon.cprwatch.shared.CprSession
import com.hackathon.cprwatch.shared.insights.ScorecardAlignedStats
import com.hackathon.cprwatch.shared.insights.SessionSummaryCalculator
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
    BackHandler { onDismiss() }

    val events = session?.compressionEvents ?: emptyList()
    if (events.isEmpty()) { onDismiss(); return }

    val scorecard = remember(events) { ScorecardAlignedStats.from(events)!! }
    val sessionSummary = remember(events) { SessionSummaryCalculator.fromCompressionEvents(events)!! }

    val grade = gradeFor(scorecard.inZonePct)
    val heuristicSummary =
        generateSummary(
            events,
            grade,
            scorecard.inZonePct,
            scorecard.avgRateBpm,
            scorecard.tooFastCount,
            scorecard.tooSlowCount,
            scorecard.sessionDurationSec,
        )

    var aiSummaryText by remember(events) { mutableStateOf<String?>(null) }
    var aiLoading by remember(events) { mutableStateOf(false) }
    var aiErrorNote by remember(events) { mutableStateOf<String?>(null) }

    LaunchedEffect(events) {
        aiSummaryText = null
        aiErrorNote = null
        val key = DevApiKeys.anthropicApiKeyOrEmpty().trim()
        if (key.isEmpty()) return@LaunchedEffect
        aiLoading = true
        val result = AnthropicInsightsClient.fetchSessionSummary(key, sessionSummary, scorecard)
        aiLoading = false
        result.fold(
            onSuccess = { aiSummaryText = it.summary },
            onFailure = { e -> aiErrorNote = e.message ?: "Summary unavailable" }
        )
    }

    val paragraphUnderHero = when {
        aiLoading -> "Fetching AI summary…"
        aiSummaryText != null -> aiSummaryText!!
        else -> heuristicSummary
    }

    val avgDepthMm = scorecard.avgDepthMm

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
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

            GradeRing(grade = grade, inZonePct = scorecard.inZonePct)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Summary",
                fontSize = 12.sp,
                color = DimText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = paragraphUnderHero,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = if (aiLoading) 0.55f else 0.85f),
                textAlign = TextAlign.Start,
                lineHeight = 22.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )

            aiErrorNote?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI unavailable — showing quick take above. ($err)",
                    fontSize = 11.sp,
                    color = GradeAmber.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            MetricsGrid(scorecard)

            Spacer(modifier = Modifier.height(24.dp))

            Text("Rate over time", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            AnnotatedRateChart(events = events, durationSec = scorecard.sessionDurationSec)

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Depth over time", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    text = "avg %.0f mm".format(avgDepthMm),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        avgDepthMm in 50.0..60.0 -> Color(0xFFFF9800)
                        else -> GradeRed
                    }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            CompressionDepthChart(events = events, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(24.dp))

            Text("Time breakdown", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(12.dp))
            TimeBreakdown(scorecard)

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Done", fontSize = 16.sp, color = Color.White, modifier = Modifier.padding(vertical = 4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GradeRing(grade: GradeInfo, inZonePct: Int) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val sw = 10.dp.toPx()
            val arcSize = Size(size.width - sw, size.height - sw)
            val tl = Offset(sw / 2, sw / 2)
            drawArc(grade.color.copy(alpha = 0.12f), -90f, 360f, false, tl, arcSize,
                style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(grade.color, -90f, 360f * inZonePct / 100f, false, tl, arcSize,
                style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(grade.letter, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = grade.color)
            Text("$inZonePct% in zone", fontSize = 13.sp, color = grade.color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun MetricsGrid(scorecard: ScorecardAlignedStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("DURATION", formatDuration(scorecard.sessionDurationSec), modifier = Modifier.weight(1f))
            MetricCard("COMPRESSIONS", "${scorecard.totalCompressions}", modifier = Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(
                "AVG RATE",
                "${scorecard.avgRateBpm}",
                subtitle = "target: 100–120 BPM",
                modifier = Modifier.weight(1f),
            )
            MetricCard(
                "LONGEST STREAK",
                formatDuration(scorecard.longestInZoneStreakSec),
                subtitle = "in target zone",
                progress = scorecard.longestStreakFractionOfSession,
                progressColor = GradeBlue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String, value: String, subtitle: String? = null,
    progress: Float? = null, progressColor: Color = GradeGreen,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, fontSize = 10.sp, color = DimText, letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (subtitle != null) { Text(subtitle, fontSize = 11.sp, color = DimText) }
            if (progress != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = progressColor, trackColor = progressColor.copy(alpha = 0.15f),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun AnnotatedRateChart(events: List<CompressionEvent>, durationSec: Long) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        if (events.size < 2) return@Canvas

        val minV = 80f; val maxV = 140f; val range = maxV - minV
        val bandTopY = size.height * (1 - (120f - minV) / range)
        val bandBottomY = size.height * (1 - (100f - minV) / range)

        drawRect(Color(0x1A6EE7A0), Offset(0f, bandTopY), Size(size.width, bandBottomY - bandTopY))

        val l120 = textMeasurer.measure("120", TextStyle(fontSize = 9.sp))
        drawText(l120, DimText, Offset(-2f, bandTopY - l120.size.height / 2))
        val l100 = textMeasurer.measure("100", TextStyle(fontSize = 9.sp))
        drawText(l100, DimText, Offset(-2f, bandBottomY - l100.size.height / 2))

        drawDashedLine(bandTopY, GradeGreen.copy(alpha = 0.25f))
        drawDashedLine(bandBottomY, GradeGreen.copy(alpha = 0.25f))

        val path = Path()
        val xStep = size.width / (events.size - 1).coerceAtLeast(1)
        events.forEachIndexed { i, e ->
            val v = e.rollingRateBpm.coerceIn(minV, maxV)
            val x = i * xStep; val y = size.height * (1 - (v - minV) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Color(0xFF4CAF50), style = Stroke(2.5.dp.toPx()))

        val peaks = findPeaks(events)
        peaks.forEach { idx ->
            val e = events[idx]
            val v = e.rollingRateBpm.coerceIn(minV, maxV)
            val x = idx * xStep; val y = size.height * (1 - (v - minV) / range)
            drawCircle(GradeRed, 4.dp.toPx(), Offset(x, y))
            val t = textMeasurer.measure("${e.rollingRateBpm.toInt()}", TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold))
            drawText(t, GradeRed, Offset(x - t.size.width / 2, y - t.size.height - 4.dp.toPx()))
        }

        if (durationSec > 0) {
            val intervals = when { durationSec > 180 -> 60L; durationSec > 60 -> 30L; else -> 15L }
            var tick = intervals
            while (tick < durationSec) {
                val frac = tick.toFloat() / durationSec; val x = frac * size.width
                val label = textMeasurer.measure("${tick / 60}:${"%02d".format(tick % 60)}", TextStyle(fontSize = 9.sp))
                drawText(label, DimText, Offset(x - label.size.width / 2, size.height + 4.dp.toPx()))
                tick += intervals
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun TimeBreakdown(scorecard: ScorecardAlignedStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BreakdownRow(
            GradeGreen,
            "In zone (100–120)",
            formatDuration(scorecard.approxWallTimeInZoneSec),
            "${scorecard.inZonePct}%",
        )
        BreakdownRow(
            GradeRed,
            "Too fast (>120)",
            formatDuration(scorecard.approxWallTimeTooFastSec),
            "${scorecard.tooFastPct}%",
        )
        BreakdownRow(
            GradeAmber,
            "Too slow (<100)",
            formatDuration(scorecard.approxWallTimeTooSlowSec),
            "${scorecard.tooSlowPct}%",
        )
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

private fun findPeaks(events: List<CompressionEvent>): List<Int> {
    if (events.size < 3) return emptyList()
    val peaks = mutableListOf<Int>()
    for (i in 1 until events.size - 1) {
        val curr = events[i].rollingRateBpm
        if (curr > events[i - 1].rollingRateBpm && curr > events[i + 1].rollingRateBpm && (curr > 120f || curr < 100f)) {
            if (peaks.isEmpty() || i - peaks.last() > events.size / 10) peaks.add(i)
        }
    }
    return peaks.take(5)
}

private fun generateSummary(
    events: List<CompressionEvent>, grade: GradeInfo, inZonePct: Int, avgRate: Int,
    tooFastCount: Int, tooSlowCount: Int, durationSec: Long
): String {
    val total = events.size
    val longestPause = if (events.size >= 2) {
        var max = 0L
        for (i in 1 until events.size) max = maxOf(max, events[i].timestampMs - events[i - 1].timestampMs)
        max / 1000
    } else 0L
    val drift = if (events.size > 9) {
        val third = events.size / 3
        events.takeLast(third).map { it.rollingRateBpm }.average().toInt() -
            events.take(third).map { it.rollingRateBpm }.average().toInt()
    } else 0

    return when (grade.letter) {
        "A" -> when {
            drift > 8 -> "Strong session \u2014 consistent rate with minor drift in the final minute"
            drift < -8 -> "Strong session \u2014 solid start with a slight slowdown toward the end"
            else -> "Excellent session \u2014 consistent rate and depth throughout"
        }
        "B" -> when {
            tooFastCount > tooSlowCount -> "Good session \u2014 you tend to speed up under fatigue."
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
    val dash = 6.dp.toPx(); val gap = 4.dp.toPx(); var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, y), Offset((x + dash).coerceAtMost(size.width), y), 1.dp.toPx())
        x += dash + gap
    }
}
