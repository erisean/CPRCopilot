package com.hackathon.cprwatch.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
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
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
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
/** Rescuer HR trend on scorecard (distinct from grade blue). */
private val RescuerHrLightBlue = Color(0xFF81D4FA)

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
        aiSummaryText != null -> aiSummaryText!!
        else -> heuristicSummary
    }

    val avgDepthMm = scorecard.avgDepthMm
    val rescuerHrSamples = remember(events) {
        events.mapNotNull { it.rescuerHrBpm?.takeIf { b -> b > 0 } }
    }

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

            Spacer(modifier = Modifier.height(16.dp))

            if (aiLoading) {
                SummaryInsightLoadingCard(modifier = Modifier.fillMaxWidth())
            } else {
                SummaryInsightCardShell(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Summary",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = GradeGreen,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = paragraphUnderHero,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.88f),
                        textAlign = TextAlign.Start,
                        lineHeight = 22.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            aiErrorNote?.let { err ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AI unavailable — showing quick take above. ($err)",
                    fontSize = 11.sp,
                    color = GradeAmber.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Heart rate over time", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    text = if (rescuerHrSamples.isEmpty()) "avg —"
                    else "avg %d bpm".format((rescuerHrSamples.sum().toDouble() / rescuerHrSamples.size).toInt()),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RescuerHrLightBlue,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AnnotatedRescuerHrChart(events = events, durationSec = scorecard.sessionDurationSec)

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
private fun SummaryInsightCardShell(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.padding(horizontal = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, GradeGreen.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            content = content,
        )
    }
}

@Composable
private fun SummaryInsightLoadingCard(modifier: Modifier = Modifier) {
    SummaryInsightCardShell(modifier = modifier) {
        Text(
            text = "Summary",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = GradeGreen,
        )
        Spacer(modifier = Modifier.height(12.dp))
        DecorativeWaveLoadingCanvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = DimText,
                strokeWidth = 2.dp,
            )
            Text(
                text = "Reading session data",
                fontSize = 13.sp,
                color = DimText,
            )
        }
    }
}

@Composable
private fun DecorativeWaveLoadingCanvas(modifier: Modifier = Modifier) {
    val accent = GradeGreen
    val transition = rememberInfiniteTransition(label = "summaryWave")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "draw",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseline = h * 0.68f
        val amp = h * 0.26f
        val seg = 96
        val pts = Array(seg + 1) { i ->
            val t = i / seg.toFloat()
            val yNorm =
                sin((t * 9.0 * PI)).toFloat() * 0.28f +
                    sin((t * 19.0 * PI + 1.1)).toFloat() * 0.14f +
                    sin((t * 5.2 * PI + 0.35)).toFloat() * 0.09f
            Offset(t * w, baseline + yNorm * amp)
        }

        val floatIdx = (progress * seg).coerceIn(0f, seg.toFloat())
        val idxFloor = floatIdx.toInt().coerceIn(0, seg - 1)
        val frac = floatIdx - idxFloor
        val tip = if (idxFloor >= seg) {
            pts[seg]
        } else {
            val a = pts[idxFloor]
            val b = pts[idxFloor + 1]
            Offset(
                a.x + (b.x - a.x) * frac,
                a.y + (b.y - a.y) * frac,
            )
        }

        val strokePath = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1..idxFloor) {
                lineTo(pts[i].x, pts[i].y)
            }
            if (frac > 0.001f && idxFloor < seg) {
                lineTo(tip.x, tip.y)
            }
        }

        val fillPath = Path().apply {
            moveTo(0f, baseline)
            lineTo(pts[0].x, pts[0].y)
            for (i in 1..idxFloor) {
                lineTo(pts[i].x, pts[i].y)
            }
            if (frac > 0.001f && idxFloor < seg) {
                lineTo(tip.x, tip.y)
            }
            lineTo(tip.x, baseline)
            close()
        }

        drawPath(
            fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    accent.copy(alpha = 0.22f),
                    accent.copy(alpha = 0.04f),
                ),
                startY = 0f,
                endY = h,
            ),
        )

        drawPath(
            strokePath,
            color = accent,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )

        val glowR = 14.dp.toPx()
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 0.85f),
                    accent.copy(alpha = 0.35f),
                    Color.Transparent,
                ),
                center = tip,
                radius = glowR,
            ),
            radius = glowR,
            center = tip,
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.75f),
            radius = 3.dp.toPx(),
            center = tip,
        )
        drawCircle(
            color = accent.copy(alpha = 0.95f),
            radius = 2.dp.toPx(),
            center = tip,
        )
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
private fun AnnotatedRescuerHrChart(events: List<CompressionEvent>, durationSec: Long) {
    val series = remember(events) { forwardFilledRescuerHr(events) }
    val textMeasurer = rememberTextMeasurer()

    when {
        series == null -> {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("No rescuer heart rate recorded", fontSize = 13.sp, color = DimText)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        events.size < 2 -> {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                Text("Heart rate chart needs at least two compressions.", fontSize = 13.sp, color = DimText)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        else -> {
            Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                val dataMin = series.minOrNull()!!
                val dataMax = series.maxOrNull()!!
                var minV = dataMin - 10f
                var maxV = dataMax + 20f
                if (maxV <= minV) {
                    val pad = 20f
                    minV -= pad
                    maxV += pad
                }
                var range = maxV - minV
                if (range < 20f) {
                    val mid = (minV + maxV) / 2f
                    minV = mid - 10f
                    maxV = mid + 10f
                    range = maxV - minV
                }

                fun yForBpm(bpm: Float): Float =
                    size.height * (1 - (bpm - minV) / range)

                val midBpm = (minV + maxV) / 2f
                drawDashedLine(yForBpm(midBpm), RescuerHrLightBlue.copy(alpha = 0.18f))

                val topLabel = textMeasurer.measure(maxV.roundToInt().toString(), TextStyle(fontSize = 9.sp))
                drawText(topLabel, DimText, Offset(-2f, yForBpm(maxV) - topLabel.size.height / 2))
                val midLabel = textMeasurer.measure(midBpm.roundToInt().toString(), TextStyle(fontSize = 9.sp))
                drawText(midLabel, DimText, Offset(-2f, yForBpm(midBpm) - midLabel.size.height / 2))
                val botLabel = textMeasurer.measure(minV.roundToInt().toString(), TextStyle(fontSize = 9.sp))
                drawText(botLabel, DimText, Offset(-2f, yForBpm(minV) - botLabel.size.height / 2))

                val path = Path()
                val fillPath = Path()
                val xStep = size.width / (events.size - 1).coerceAtLeast(1)

                series.forEachIndexed { i, raw ->
                    val v = raw.coerceIn(minV, maxV)
                    val x = i * xStep
                    val y = yForBpm(v)
                    if (i == 0) {
                        path.moveTo(x, y)
                        fillPath.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo((events.size - 1) * xStep, size.height)
                fillPath.lineTo(0f, size.height)
                fillPath.close()

                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            RescuerHrLightBlue.copy(alpha = 0.30f),
                            RescuerHrLightBlue.copy(alpha = 0.04f),
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
                drawPath(
                    path,
                    RescuerHrLightBlue,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )

                events.forEachIndexed { i, e ->
                    val hr = e.rescuerHrBpm?.takeIf { it > 0 } ?: return@forEachIndexed
                    val v = hr.toFloat().coerceIn(minV, maxV)
                    val x = i * xStep
                    val y = yForBpm(v)
                    drawCircle(RescuerHrLightBlue, 3.dp.toPx(), Offset(x, y))
                }

                if (durationSec > 0) {
                    val intervals = when { durationSec > 180 -> 60L; durationSec > 60 -> 30L; else -> 15L }
                    var tick = intervals
                    while (tick < durationSec) {
                        val frac = tick.toFloat() / durationSec
                        val x = frac * size.width
                        val label = textMeasurer.measure(
                            "${tick / 60}:${"%02d".format(tick % 60)}",
                            TextStyle(fontSize = 9.sp),
                        )
                        drawText(label, DimText, Offset(x - label.size.width / 2, size.height + 4.dp.toPx()))
                        tick += intervals
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
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

/** Last-known forward fill so sparse HR samples (every Nth compression) still draw a continuous line. */
private fun forwardFilledRescuerHr(events: List<CompressionEvent>): List<Float>? {
    var carry: Float? = null
    val partial = events.map { e ->
        val r = e.rescuerHrBpm?.takeIf { it > 0 }?.toFloat()
        if (r != null) carry = r
        carry
    }
    val firstIdx = partial.indexOfFirst { it != null }
    if (firstIdx < 0) return null
    val seed = partial[firstIdx]!!
    var c = seed
    return List(events.size) { i ->
        val v = partial[i]
        when {
            v != null -> {
                c = v
                c
            }
            i < firstIdx -> seed
            else -> c
        }
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
