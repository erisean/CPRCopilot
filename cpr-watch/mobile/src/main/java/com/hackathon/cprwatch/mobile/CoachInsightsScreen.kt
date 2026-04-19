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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackathon.cprwatch.shared.CompressionEvent
import com.hackathon.cprwatch.shared.CprSession
import com.hackathon.cprwatch.shared.insights.InsightItem
import com.hackathon.cprwatch.shared.insights.InsightStatus
import com.hackathon.cprwatch.shared.insights.OverallGrade
import com.hackathon.cprwatch.shared.insights.SessionInsights
import com.hackathon.cprwatch.shared.insights.SessionSummaryCalculator

private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)
private val DimText = Color(0xFF6B6B6B)
private val GradeGreen = Color(0xFF6EE7A0)
private val GradeBlue = Color(0xFF85B7EB)
private val GradeAmber = Color(0xFFF5A623)
private val GradeRed = Color(0xFFE24B4A)

private data class CoachGradeUi(val letter: String, val color: Color, val label: String)

private fun coachGradeUiFromOverall(g: OverallGrade): CoachGradeUi = when (g) {
    OverallGrade.A -> CoachGradeUi("A", GradeGreen, "Excellent")
    OverallGrade.B -> CoachGradeUi("B", GradeBlue, "Good")
    OverallGrade.C -> CoachGradeUi("C", GradeAmber, "Needs work")
    OverallGrade.D -> CoachGradeUi("D", GradeRed, "Needs work")
    OverallGrade.F -> CoachGradeUi("F", GradeRed, "Keep practicing")
}

/**
 * Separate screen: Claude coach only (does not modify [ScorecardScreen]).
 */
@Composable
fun CoachInsightsScreen(
    session: CprSession?,
    onDismiss: () -> Unit
) {
    val events = session?.compressionEvents ?: emptyList()
    if (events.isEmpty()) {
        onDismiss()
        return
    }

    val inZoneCount = events.count { it.rollingRateBpm in 100f..120f }
    val inZonePct = inZoneCount * 100 / events.size

    var insights by remember(events) { mutableStateOf<SessionInsights?>(null) }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(events) {
        insights = null
        note = null
        val key = DevApiKeys.anthropicApiKeyOrEmpty().trim()
        if (key.isEmpty()) {
            note = "Add ANTHROPIC_API_KEY in local.properties (debug) for Claude coach insights."
            return@LaunchedEffect
        }
        val sessionAggregate = SessionSummaryCalculator.fromCompressionEvents(events) ?: return@LaunchedEffect
        loading = true
        val result = AnthropicInsightsClient.fetchInsights(key, sessionAggregate)
        loading = false
        result.fold(
            onSuccess = { insights = it },
            onFailure = { e ->
                note = "Claude request failed: ${e.message}"
                insights = null
            }
        )
    }

    val ringGrade = insights?.let { coachGradeUiFromOverall(it.overallGrade) }
        ?: CoachGradeUi("…", DimText, "")

    val summaryText = when {
        insights != null -> insights!!.overallSummary
        loading -> "Fetching coach summary…"
        else -> ""
    }

    Surface(modifier = Modifier.fillMaxSize(), color = DarkBg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Coach insights", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Box(
                    modifier = Modifier
                        .background(CardBg, RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Claude", fontSize = 12.sp, color = DimText)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            CoachGradeRing(grade = ringGrade, inZonePct = inZonePct)

            Spacer(modifier = Modifier.height(16.dp))

            if (summaryText.isNotEmpty()) {
                Text(
                    text = summaryText,
                    fontSize = 15.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            note?.let { n ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(n, fontSize = 12.sp, color = GradeAmber.copy(alpha = 0.95f), modifier = Modifier.fillMaxWidth())
            }

            insights?.let { si ->
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "Overall grade — ${si.overallGrade}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (si.insights.isEmpty()) {
                    Text(
                        "No separate metric cards in this response (empty insights array).",
                        fontSize = 12.sp,
                        color = DimText,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }
                si.insights.forEach { item ->
                    CoachInsightCard(item)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Priority: ${si.topPriority}",
                    fontSize = 13.sp,
                    color = GradeBlue.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                )
            }

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
private fun CoachGradeRing(grade: CoachGradeUi, inZonePct: Int) {
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
            Text("$inZonePct% in zone (rate)", fontSize = 12.sp, color = grade.color.copy(alpha = 0.75f))
        }
    }
}

@Composable
private fun CoachInsightCard(item: InsightItem) {
    val accent = when (item.status) {
        InsightStatus.GOOD -> GradeGreen
        InsightStatus.WARNING -> GradeAmber
        InsightStatus.CRITICAL -> GradeRed
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(item.status.name, fontSize = 11.sp, color = accent)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.metricValue, fontSize = 12.sp, color = DimText)
            Spacer(modifier = Modifier.height(10.dp))
            Text(item.explanation, fontSize = 13.sp, color = Color.White.copy(alpha = 0.82f), lineHeight = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(item.recommendation, fontSize = 13.sp, color = accent.copy(alpha = 0.95f), lineHeight = 18.sp)
        }
    }
}
