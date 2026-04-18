package com.hackathon.cprwatch.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private val viewModel: CprViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsState()
                CprDashboard(state)
            }
        }
    }
}

@Composable
private fun CprDashboard(state: MobileUiState) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "CPR Coach",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live status
            LiveStatusCard(state)

            Spacer(modifier = Modifier.height(16.dp))

            val dataPoints = state.currentSession?.dataPoints
                ?: state.pastSessions.lastOrNull()?.dataPoints
                ?: emptyList()

            if (dataPoints.isNotEmpty()) {
                RateChart(dataPoints = dataPoints)
                Spacer(modifier = Modifier.height(8.dp))
                DepthChart(dataPoints = dataPoints)
                Spacer(modifier = Modifier.height(16.dp))
                SessionStats(dataPoints)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Start a CPR session on your watch\nto see data here",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        lineHeight = 24.sp
                    )
                }
            }

            // Past sessions summary
            if (state.pastSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Past Sessions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                state.pastSessions.reversed().forEachIndexed { index, session ->
                    PastSessionCard(index = state.pastSessions.size - index, session = session)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun LiveStatusCard(state: MobileUiState) {
    val isLive = state.currentSession != null
    val latest = state.latestDataPoint

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isLive) Color(0xFF1B5E20) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (isLive) Color.Green else Color.Gray,
                                CircleShape
                            )
                    )
                    Text(
                        text = if (isLive) "  LIVE" else "  Waiting for watch",
                        fontWeight = FontWeight.Bold,
                        color = if (isLive) Color.White else Color.Gray
                    )
                }
                if (latest != null) {
                    Text(
                        text = latest.feedback,
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (latest != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${latest.rate}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "BPM",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionStats(dataPoints: List<com.hackathon.cprwatch.shared.CprDataPoint>) {
    val avgRate = dataPoints.map { it.rate }.average().toInt()
    val avgDepth = dataPoints.map { it.depthCm.toDouble() }.average()
    val goodCount = dataPoints.count { it.feedback == "Good compressions!" }
    val goodPct = if (dataPoints.isNotEmpty()) (goodCount * 100 / dataPoints.size) else 0
    val durationSec = if (dataPoints.size >= 2) {
        (dataPoints.last().timestampMs - dataPoints.first().timestampMs) / 1000
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Session Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            StatRow("Duration", "${durationSec}s")
            StatRow("Avg Rate", "$avgRate BPM")
            StatRow("Avg Depth", "%.1f cm".format(avgDepth))
            StatRow("Good Compressions", "$goodPct%")
            StatRow("Total Compressions", "${dataPoints.size}")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray)
        Text(text = value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PastSessionCard(
    index: Int,
    session: com.hackathon.cprwatch.shared.CprSession
) {
    val avgRate = session.dataPoints.map { it.rate }.average().toInt()
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
                Text(
                    text = "Session #$index",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${session.dataPoints.size} compressions, ${durationSec}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Text(
                text = "$avgRate BPM",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}
