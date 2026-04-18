package com.hackathon.cprwatch.mobile

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackathon.cprwatch.shared.CprSession

@Composable
fun IdleScreen(
    pastSessions: List<CprSession>,
    onStartDebug: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "CPR Coach",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Waiting for watch session",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Start a CPR session on your watch,\nor use debug mode to preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(onClick = onStartDebug) {
                Text("Start Debug Session")
            }

            if (pastSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = "Past Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                pastSessions.reversed().forEachIndexed { index, session ->
                    PastSessionRow(
                        index = pastSessions.size - index,
                        session = session
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PastSessionRow(index: Int, session: CprSession) {
    val avgRate = session.dataPoints.map { it.rate }.average().toInt()
    val inZone = session.dataPoints.count { it.rate in 100..120 }
    val inZonePct = if (session.dataPoints.isNotEmpty()) inZone * 100 / session.dataPoints.size else 0
    val durationSec = if (session.dataPoints.size >= 2) {
        (session.dataPoints.last().timestampMs - session.dataPoints.first().timestampMs) / 1000
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Session #$index", fontWeight = FontWeight.Bold)
                Text(
                    "${session.dataPoints.size} compressions · ${formatDuration(durationSec)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$avgRate BPM", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "$inZonePct% in zone",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (inZonePct >= 80) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )
            }
        }
    }
}

internal fun formatDuration(seconds: Long): String {
    val min = seconds / 60
    val sec = seconds % 60
    return if (min > 0) "${min}m ${sec}s" else "${sec}s"
}
