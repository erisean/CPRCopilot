package com.hackathon.cprwatch.mobile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackathon.cprwatch.shared.CompressionEvent
import com.hackathon.cprwatch.shared.CprSession

private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)
private val DimText = Color(0xFF6B6B6B)
private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFF44336)
private val Orange = Color(0xFFFF9800)

@Composable
fun LiveSessionScreen(
    session: CprSession?,
    latestEvent: CompressionEvent?,
    surfaceCalibrated: Boolean = false,
    surfaceCalibrationProgress: Float = 0f,
    surfaceProfile: MobileSurfaceProfile? = null,
    onStopSession: () -> Unit
) {
    val events = session?.compressionEvents ?: emptyList()
    val rate = latestEvent?.rollingRateBpm?.toInt() ?: 0
    val instruction = latestEvent?.instruction ?: "none"
    val currentDepthMm = latestEvent?.estimatedDepthMm ?: 0f

    val status = when {
        rate == 0 -> RateStatus.WAITING
        rate in 100..120 -> RateStatus.IN_ZONE
        rate < 100 -> RateStatus.TOO_SLOW
        else -> RateStatus.TOO_FAST
    }

    val feedback = when (instruction) {
        "none" -> if (rate > 0) "Good compressions!" else ""
        "faster" -> "Push faster"
        "slower" -> "Slow down"
        "push_harder" -> "Push harder"
        "ease_up" -> "Ease up"
        "let_chest_up" -> "Let chest recoil"
        "resume_compressions" -> "Resume compressions!"
        "switch_rescuers" -> "Switch rescuers"
        "consider_switching" -> "Consider switching"
        "stay_strong" -> "Stay strong!"
        else -> "Good compressions!"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .height(8.dp)
                            .background(Green, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LIVE SESSION", color = Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onStopSession,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Red.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("End Session", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Surface calibration banner
            if (!surfaceCalibrated && surfaceCalibrationProgress > 0f) {
                SurfaceCalibrationBanner(progress = surfaceCalibrationProgress)
                Spacer(modifier = Modifier.height(8.dp))
            } else if (surfaceCalibrated && surfaceProfile != null) {
                SurfaceBadge(profile = surfaceProfile)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // BPM hero with pulse
            HeroBpm(rate = rate, status = status, compressionCount = events.size)

            Spacer(modifier = Modifier.height(12.dp))

            // Rate chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Rate", fontSize = 11.sp, color = DimText)
                Text("target 100–120", fontSize = 10.sp, color = DimText)
            }
            Spacer(modifier = Modifier.height(2.dp))
            CompressionRateChart(events = events, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(4.dp))

            // Depth chart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Depth", fontSize = 11.sp, color = DimText)
                Text(
                    text = if (currentDepthMm > 0) "%.0f mm".format(currentDepthMm) else "--",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        currentDepthMm == 0f -> Color.Gray
                        currentDepthMm in 50f..60f -> Orange
                        else -> Red
                    }
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            CompressionDepthChart(events = events, modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(8.dp))

            // Stats grid
            StatsGrid(
                events = events,
                rescuerHr = rescuerHrForDisplay(latestEvent, events),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Guidance bar
            GuidanceBar(feedback = feedback, status = status)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

private enum class RateStatus {
    WAITING, IN_ZONE, TOO_SLOW, TOO_FAST;

    val label: String get() = when (this) {
        WAITING -> "Waiting"
        IN_ZONE -> "In Zone"
        TOO_SLOW -> "Too Slow"
        TOO_FAST -> "Too Fast"
    }

    val color: Color get() = when (this) {
        WAITING -> Color.Gray
        IN_ZONE -> Green
        TOO_SLOW -> Orange
        TOO_FAST -> Red
    }
}

@Composable
private fun HeroBpm(rate: Int, status: RateStatus, compressionCount: Int) {
    val badgeColor by animateColorAsState(targetValue = status.color, label = "badge")

    val pulseScale = remember { Animatable(1f) }
    val pulseAlpha = remember { Animatable(0.15f) }
    val rippleScale = remember { Animatable(1f) }
    val rippleAlpha = remember { Animatable(0f) }

    LaunchedEffect(compressionCount) {
        if (compressionCount > 0) {
            coroutineScope {
                launch {
                    pulseScale.snapTo(1.12f)
                    pulseAlpha.snapTo(0.5f)
                    pulseScale.animateTo(1f, tween(350))
                }
                launch {
                    pulseAlpha.snapTo(0.5f)
                    pulseAlpha.animateTo(0.15f, tween(400))
                }
                launch {
                    rippleScale.snapTo(1f)
                    rippleAlpha.snapTo(0.3f)
                    rippleScale.animateTo(1.35f, tween(500))
                }
                launch {
                    rippleAlpha.snapTo(0.3f)
                    rippleAlpha.animateTo(0f, tween(500))
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status badge above
        Box(
            modifier = Modifier
                .offset(y = (-8).dp)
                .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 5.dp)
        ) {
            Text(status.label, color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Pulsing circle with ripple + BPM
        Box(contentAlignment = Alignment.Center) {
            // Ripple ring (expands outward and fades)
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(rippleScale.value)
                    .alpha(rippleAlpha.value)
                    .border(1.5.dp, badgeColor, CircleShape)
            )
            // Main pulse ring
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(pulseScale.value)
                    .alpha(pulseAlpha.value)
                    .border(2.dp, badgeColor, CircleShape)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (rate > 0) "$rate" else "--",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    lineHeight = 56.sp
                )
                Text("BPM", fontSize = 13.sp, color = DimText, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Latest beat’s HR, or most recent prior non-null HR (avoids “--” on sparse samples). */
private fun rescuerHrForDisplay(latest: CompressionEvent?, events: List<CompressionEvent>): Int? {
    latest?.rescuerHrBpm?.takeIf { it > 0 }?.let { return it }
    for (i in events.indices.reversed()) {
        events[i].rescuerHrBpm?.takeIf { it > 0 }?.let { return it }
    }
    return null
}

@Composable
private fun StatsGrid(events: List<CompressionEvent>, rescuerHr: Int?) {
    val count = events.size
    val inZoneCount = events.count { it.rollingRateBpm in 100f..120f }
    val inZonePct = if (count > 0) inZoneCount * 100 / count else 0
    val avgRate = if (count > 0) events.map { it.rollingRateBpm }.average().toInt() else 0
    val durationSec = if (events.size >= 2) {
        (events.last().timestampMs - events.first().timestampMs) / 1000
    } else 0L

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard("Compressions", "$count", modifier = Modifier.weight(1f))
        StatCard("Duration", formatDuration(durationSec), modifier = Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            "In Zone",
            "$inZonePct%",
            valueColor = when {
                inZonePct >= 80 -> Green
                inZonePct >= 50 -> Orange
                count == 0 -> Color.White
                else -> Red
            },
            modifier = Modifier.weight(1f)
        )
        StatCard("Avg Rate", if (avgRate > 0) "$avgRate" else "--", modifier = Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            "\u2764\uFE0F Rescuer HR",
            if (rescuerHr != null && rescuerHr > 0) "$rescuerHr" else "--",
            valueColor = when {
                rescuerHr == null || rescuerHr == 0 -> Color.White
                rescuerHr > 150 -> Red
                rescuerHr > 130 -> Orange
                else -> Green
            },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.weight(1f))
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
        colors = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = label, fontSize = 11.sp, color = DimText, letterSpacing = 0.3.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun SurfaceCalibrationBanner(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF00BCD4).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Calibrating surface...",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF00BCD4)
                )
                Text(
                    "Keep compressing naturally",
                    fontSize = 11.sp,
                    color = DimText
                )
            }
            Text(
                "${"%.0f".format(progress * 100)}%",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00BCD4)
            )
        }
    }
}

@Composable
private fun SurfaceBadge(profile: MobileSurfaceProfile) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF00BCD4).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    profile.surfaceLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF00BCD4)
                )
                Text(
                    "Target: ${profile.targetDepthMinMm.toInt()}–${profile.targetDepthMaxMm.toInt()} mm",
                    fontSize = 11.sp,
                    color = DimText
                )
            }
            Text(
                "\u2713",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00BCD4)
            )
        }
    }
}

@Composable
private fun GuidanceBar(feedback: String, status: RateStatus) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(status.color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("GUIDANCE", fontSize = 9.sp, color = DimText, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = feedback.ifEmpty { "Waiting..." },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = status.color
            )
        }
    }
}
