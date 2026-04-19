package com.hackathon.cprwatch.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
    val feedbackColor = when (state.feedback) {
        CompressionFeedback.GOOD -> Color.Green
        CompressionFeedback.IDLE -> Color.Gray
        CompressionFeedback.CALIBRATING -> Color.Cyan
        else -> Color.Yellow
    }

    Box(contentAlignment = Alignment.Center) {
        PulseRing(beatId = state.metronomeBeatId, color = feedbackColor)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(8.dp)
        ) {
        // Rate display
        Text(
            text = "${state.rate}",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = feedbackColor
        )
        Text(
            text = "BPM",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Depth
        if (state.depthCm > 0) {
            Text(
                text = "%.1f cm".format(state.depthCm),
                fontSize = 14.sp,
                color = Color.White
            )
        }

        // Heart rate
        if (state.rescuerHr > 0) {
            Text(
                text = "\u2764 ${state.rescuerHr}",
                fontSize = 12.sp,
                color = Color.Red
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Feedback message
        Text(
            text = state.feedbackMessage,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = feedbackColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Accel (m/s^2)",
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text(
            text = "x ${"%.2f".format(state.accelX)}  y ${"%.2f".format(state.accelY)}",
            fontSize = 10.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "z ${"%.2f".format(state.accelZ)}  |a| ${"%.2f".format(state.accelMagnitude)}",
            fontSize = 10.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (state.sendError != null) "Send err: ${state.sendError}"
                   else "Sent: ${state.messagesSent}",
            fontSize = 9.sp,
            color = if (state.sendError != null) Color.Red else Color(0xFF4CAF50),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Stop button
        Button(
            onClick = onStop,
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.DarkGray
            )
        ) {
            Text("■", fontSize = 14.sp)
        }
        }
    }
}

@Composable
private fun PulseRing(beatId: Long, color: Color) {
    val progress = remember { Animatable(1f) }

    LaunchedEffect(beatId) {
        if (beatId <= 0L) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
        )
    }

    Canvas(modifier = Modifier.size(170.dp)) {
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
