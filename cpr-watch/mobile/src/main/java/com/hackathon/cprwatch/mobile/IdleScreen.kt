package com.hackathon.cprwatch.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackathon.cprwatch.shared.CprSession
import java.util.Calendar

private val DarkBg = Color(0xFF0D0D0D)
private val CardBg = Color(0xFF1A1A1A)
private val DimText = Color(0xFF6B6B6B)

@Composable
fun IdleScreen(
    pastSessions: List<CprSession>,
    watchConnected: Boolean,
    watchName: String?,
    onStartSession: () -> Unit,
    onStartDebug: () -> Unit
) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = greeting,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(CardBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Watch connection status
            WatchConnectionBanner(connected = watchConnected, watchName = watchName)

            Spacer(modifier = Modifier.height(12.dp))

            // Infant mode toggle
            InfantModeToggle()

            // Main start button area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBg)
                    .clickable { onStartSession() },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Circular button outline
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .border(2.dp, Color(0xFF333333), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Start\nsession",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                lineHeight = 34.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "tap anywhere to begin",
                                fontSize = 12.sp,
                                color = DimText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "\"hey google, start CPR\"",
                        fontSize = 12.sp,
                        color = DimText,
                        fontWeight = FontWeight.Light
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "debug mode",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onStartDebug() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stats bar
            StatsBar(pastSessions = pastSessions)

            Spacer(modifier = Modifier.height(12.dp))

            // Session history link
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardBg)
                    .clickable { }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("See session history", color = Color.White, fontSize = 14.sp)
                    Text("›", color = DimText, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun InfantModeToggle() {
    var infantMode by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("👶", fontSize = 18.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Infant mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                "Off · 100–120 BPM for adults",
                color = DimText,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = infantMode,
            onCheckedChange = { infantMode = it },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF4CAF50),
                uncheckedTrackColor = Color(0xFF333333),
                uncheckedThumbColor = Color(0xFF666666)
            )
        )
    }
}

@Composable
private fun StatsBar(pastSessions: List<CprSession>) {
    val sessionCount = pastSessions.size
    val bestGrade = if (pastSessions.isNotEmpty()) {
        val bestPct = pastSessions.maxOf { session ->
            val events = session.compressionEvents
            if (events.isEmpty()) 0
            else events.count { it.rollingRateBpm in 100f..120f } * 100 / events.size
        }
        when {
            bestPct >= 80 -> "A"
            bestPct >= 65 -> "B"
            bestPct >= 45 -> "C"
            else -> "D"
        }
    } else "–"

    val avgInZone = if (pastSessions.isNotEmpty()) {
        val allEvents = pastSessions.flatMap { it.compressionEvents }
        if (allEvents.isNotEmpty()) {
            allEvents.count { it.rollingRateBpm in 100f..120f } * 100 / allEvents.size
        } else 0
    } else 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(value = "$sessionCount", label = "SESSIONS")
        StatItem(value = bestGrade, label = "BEST GRADE")
        StatItem(value = "${avgInZone}%", label = "AVG IN ZONE")
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = DimText,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun WatchConnectionBanner(connected: Boolean, watchName: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (connected) Color(0xFF4CAF50) else Color(0xFFF44336),
                    CircleShape
                )
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (connected) "Watch connected" else "Watch not connected",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (connected) (watchName ?: "Galaxy Watch") else "Check Bluetooth pairing",
                color = DimText,
                fontSize = 12.sp
            )
        }
        Text(
            text = if (connected) "⌚" else "?",
            fontSize = 20.sp
        )
    }
}

internal fun formatDuration(seconds: Long): String {
    val min = seconds / 60
    val sec = seconds % 60
    return if (min > 0) "$min:${"%02d".format(sec)}" else "${sec}s"
}
