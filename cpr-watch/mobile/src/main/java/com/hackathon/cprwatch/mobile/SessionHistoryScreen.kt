package com.hackathon.cprwatch.mobile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackathon.cprwatch.shared.CprSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)
private val DimText = Color(0xFF6B6B6B)
private val GradeGreen = Color(0xFF6EE7A0)
private val GradeBlue = Color(0xFF85B7EB)
private val GradeAmber = Color(0xFFF5A623)
private val GradeRed = Color(0xFFE24B4A)

@Composable
fun SessionHistoryScreen(
    pastSessions: List<CprSession>,
    onSelectSession: (CprSession) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            // Header
            Text(
                text = "Session History",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${pastSessions.size} sessions",
                fontSize = 13.sp,
                color = DimText
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (pastSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No sessions yet",
                        color = DimText,
                        fontSize = 16.sp
                    )
                }
            } else {
                pastSessions.reversed().forEachIndexed { index, session ->
                    SessionCard(
                        index = pastSessions.size - index,
                        session = session,
                        onClick = { onSelectSession(session) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    index: Int,
    session: CprSession,
    onClick: () -> Unit
) {
    val events = session.compressionEvents
    val count = events.size
    val inZone = events.count { it.rollingRateBpm in 100f..120f }
    val inZonePct = if (count > 0) inZone * 100 / count else 0
    val avgRate = if (count > 0) events.map { it.rollingRateBpm }.average().toInt() else 0
    val durationSec = if (events.size >= 2)
        (events.last().timestampMs - events.first().timestampMs) / 1000 else 0L

    val grade = when {
        inZonePct >= 80 -> "A" to GradeGreen
        inZonePct >= 65 -> "B" to GradeBlue
        inZonePct >= 45 -> "C" to GradeAmber
        else -> "D" to GradeRed
    }

    val timeStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        .format(Date(session.startTimeMs))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Grade circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(grade.second.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = grade.first,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = grade.second
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Session info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Session #$index",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = timeStr,
                fontSize = 12.sp,
                color = DimText
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$count compressions",
                    fontSize = 11.sp,
                    color = DimText
                )
                Text(
                    text = formatDuration(durationSec),
                    fontSize = 11.sp,
                    color = DimText
                )
            }
        }

        // Stats
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$avgRate",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "BPM",
                fontSize = 10.sp,
                color = DimText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$inZonePct% in zone",
                fontSize = 11.sp,
                color = grade.second
            )
        }
    }
}
