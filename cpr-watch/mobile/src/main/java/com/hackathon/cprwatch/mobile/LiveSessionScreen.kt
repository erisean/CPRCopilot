package com.hackathon.cprwatch.mobile

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackathon.cprwatch.shared.CprDataPoint
import com.hackathon.cprwatch.shared.CprSession

private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFF44336)
private val Orange = Color(0xFFFF9800)
private val DarkGreen = Color(0xFF1B5E20)

@Composable
fun LiveSessionScreen(
    session: CprSession?,
    latestDataPoint: CprDataPoint?,
    isSimulating: Boolean,
    onStopDebug: () -> Unit
) {
    val dataPoints = session?.dataPoints ?: emptyList()
    val rate = latestDataPoint?.rate ?: 0
    val feedback = latestDataPoint?.feedback ?: ""

    val status = when {
        rate == 0 -> RateStatus.WAITING
        rate in 100..120 -> RateStatus.IN_ZONE
        rate < 100 -> RateStatus.TOO_SLOW
        else -> RateStatus.TOO_FAST
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE SESSION",
                        color = Green,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                if (isSimulating) {
                    OutlinedButton(
                        onClick = onStopDebug,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red)
                    ) {
                        Text("End Session", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Hero BPM
            HeroBpm(rate = rate, status = status)

            Spacer(modifier = Modifier.height(24.dp))

            // Rate chart
            RateChart(
                dataPoints = dataPoints,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            StatsGrid(dataPoints = dataPoints, session = session)

            Spacer(modifier = Modifier.height(16.dp))

            // Guidance bar
            GuidanceBar(feedback = feedback, status = status)
        }
    }
}

private enum class RateStatus { WAITING, IN_ZONE, TOO_SLOW, TOO_FAST }

@Composable
private fun PulsingDot() {
    Box(
        modifier = Modifier
            .width(10.dp)
            .height(10.dp)
            .background(Green, RoundedCornerShape(5.dp))
    )
}

@Composable
private fun HeroBpm(rate: Int, status: RateStatus) {
    val badgeColor by animateColorAsState(
        targetValue = when (status) {
            RateStatus.IN_ZONE -> Green
            RateStatus.TOO_SLOW -> Orange
            RateStatus.TOO_FAST -> Red
            RateStatus.WAITING -> Color.Gray
        },
        label = "badge"
    )
    val badgeText = when (status) {
        RateStatus.IN_ZONE -> "In Zone"
        RateStatus.TOO_SLOW -> "Too Slow"
        RateStatus.TOO_FAST -> "Too Fast"
        RateStatus.WAITING -> "Waiting"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = badgeText,
                color = badgeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (rate > 0) "$rate" else "--",
            fontSize = 96.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 96.sp
        )

        Text(
            text = "BPM",
            fontSize = 18.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatsGrid(dataPoints: List<CprDataPoint>, session: CprSession?) {
    val compressionCount = dataPoints.size
    val inZoneCount = dataPoints.count { it.rate in 100..120 }
    val inZonePct = if (compressionCount > 0) inZoneCount * 100 / compressionCount else 0
    val avgRate = if (compressionCount > 0) dataPoints.map { it.rate }.average().toInt() else 0
    val durationSec = if (dataPoints.size >= 2) {
        (dataPoints.last().timestampMs - dataPoints.first().timestampMs) / 1000
    } else 0L

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            label = "Compressions",
            value = "$compressionCount",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Duration",
            value = formatDuration(durationSec),
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            label = "In Zone",
            value = "$inZonePct%",
            valueColor = when {
                inZonePct >= 80 -> Green
                inZonePct >= 50 -> Orange
                compressionCount == 0 -> Color.White
                else -> Red
            },
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Avg Rate",
            value = if (avgRate > 0) "$avgRate" else "--",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
private fun GuidanceBar(feedback: String, status: RateStatus) {
    val bgColor = when (status) {
        RateStatus.IN_ZONE -> Green.copy(alpha = 0.15f)
        RateStatus.TOO_SLOW -> Orange.copy(alpha = 0.15f)
        RateStatus.TOO_FAST -> Red.copy(alpha = 0.15f)
        RateStatus.WAITING -> Color.Gray.copy(alpha = 0.15f)
    }
    val textColor = when (status) {
        RateStatus.IN_ZONE -> Green
        RateStatus.TOO_SLOW -> Orange
        RateStatus.TOO_FAST -> Red
        RateStatus.WAITING -> Color.Gray
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "CURRENT GUIDANCE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = feedback.ifEmpty { "Waiting for compressions..." },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center
            )
        }
    }
}
