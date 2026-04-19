package com.hackathon.cprwatch.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.hackathon.cprwatch.sensor.CompressionFeedback

@Composable
fun CprScreen(
    state: CprUiState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!state.isActive) {
            IdleScreen(onStart = onStart)
        } else {
            ActiveScreen(state = state, onStop = onStop)
        }
    }
}

@Composable
private fun IdleScreen(onStart: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "CPR",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Coach",
            fontSize = 16.sp,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red
            )
        ) {
            Text("GO", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ActiveScreen(state: CprUiState, onStop: () -> Unit) {
    val pace = paceState(state.rate)
    val roundSafePadding = 14.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(roundSafePadding)
    ) {
        PulseRing(
            beatId = state.metronomeBeatId,
            color = pace.color,
            modifier = Modifier.align(Alignment.Center)
        )

        Button(
            onClick = onStop,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(vertical = 4.dp, horizontal = 25.dp)
                .height(28.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5A5A5A))
        ) {
            Text("End", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = pace.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = pace.color
            )
            Text(
                text = "${state.rate}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = pace.color,
                lineHeight = 48.sp
            )
            Text(
                text = "BPM",
                fontSize = 11.sp,
                color = Color.LightGray
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        StatBox(
           // title = "Depth Guide",
            value = depthGuide(state.depthGuidanceFeedback),
            valueColor = depthGuideColor(state.depthGuidanceFeedback),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.9f)
                .padding(bottom = 2.dp)
        )
    }
}

@Composable
private fun StatBox(
   // title: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(44.dp)
            .background(Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF3A3A3A), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        //Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private data class PaceState(
    val label: String,
    val color: Color
)

private fun paceState(rate: Int): PaceState {
    return when {
        rate > 120 -> PaceState(label = "Too Fast", color = Color.Red)
        rate in 100..120 -> PaceState(label = "In Zone", color = Color(0xFF4CAF50))
        else -> PaceState(label = "Too Slow", color = Color(0xFFFFC107))
    }
}

private fun depthGuide(feedback: CompressionFeedback): String {
    return when (feedback) {
        CompressionFeedback.TOO_SHALLOW -> "Press Harder"
        CompressionFeedback.TOO_DEEP -> "Press Softer"
        CompressionFeedback.CALIBRATING -> "Begin compressions"
        else -> "Depth good"
    }
}

private fun depthGuideColor(feedback: CompressionFeedback): Color {
    return when (feedback) {
        CompressionFeedback.TOO_SHALLOW, CompressionFeedback.TOO_DEEP -> Color(0xFFFFC107)
        CompressionFeedback.CALIBRATING -> Color.Cyan
        else -> Color(0xFF4CAF50)
    }
}

@Composable
private fun PulseRing(beatId: Long, color: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(beatId) {
        if (beatId <= 0L) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = modifier.size(170.dp)) {
        val t = progress.value
        val radius = size.minDimension * (0.22f + 0.34f * t)
        val alpha = (1f - t) * 0.55f
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = radius,
            style = Stroke(width = 5.dp.toPx())
        )
    }
}
