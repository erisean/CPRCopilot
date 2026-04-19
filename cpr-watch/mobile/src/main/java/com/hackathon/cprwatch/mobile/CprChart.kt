package com.hackathon.cprwatch.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hackathon.cprwatch.shared.CompressionEvent

@Composable
fun CompressionRateChart(
    events: List<CompressionEvent>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .height(160.dp)
            .padding(horizontal = 4.dp)
    ) {
        if (events.isEmpty()) return@Canvas

        val minValue = 60f
        val maxValue = 160f
        val range = maxValue - minValue
        val bandTopY = size.height * (1 - (120f - minValue) / range)
        val bandBottomY = size.height * (1 - (100f - minValue) / range)

        // Target zone
        drawRect(
            color = Color(0x1A4CAF50),
            topLeft = Offset(0f, bandTopY),
            size = androidx.compose.ui.geometry.Size(size.width, bandBottomY - bandTopY)
        )

        drawDashedLine(bandTopY, Color(0xFF4CAF50).copy(alpha = 0.3f))
        drawDashedLine(bandBottomY, Color(0xFF4CAF50).copy(alpha = 0.3f))

        if (events.size < 2) return@Canvas

        val path = Path()
        val xStep = size.width / (events.size - 1).coerceAtLeast(1)

        events.forEachIndexed { i, event ->
            val value = event.rollingRateBpm.coerceIn(minValue, maxValue)
            val x = i * xStep
            val y = size.height * (1 - (value - minValue) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, Color(0xFF42A5F5), style = Stroke(width = 2.5.dp.toPx()))

        events.forEachIndexed { i, event ->
            val value = event.rollingRateBpm.coerceIn(minValue, maxValue)
            val x = i * xStep
            val y = size.height * (1 - (value - minValue) / range)
            val inBand = value in 100f..120f
            drawCircle(
                color = if (inBand) Color(0xFF4CAF50) else Color(0xFFF44336),
                radius = 2.5.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

@Composable
fun CompressionDepthChart(
    events: List<CompressionEvent>,
    modifier: Modifier = Modifier,
    targetMinMm: Float = 50f,
    targetMaxMm: Float = 60f
) {
    Canvas(
        modifier = modifier
            .height(120.dp)
            .padding(horizontal = 4.dp)
    ) {
        if (events.isEmpty()) return@Canvas

        val minValue = 0f
        val maxValue = (targetMaxMm * 1.5f).coerceAtLeast(80f)
        val range = maxValue - minValue
        if (events.size < 2) return@Canvas

        val path = Path()
        val xStep = size.width / (events.size - 1).coerceAtLeast(1)

        events.forEachIndexed { i, event ->
            val value = event.estimatedDepthMm.coerceIn(minValue, maxValue)
            val x = i * xStep
            val y = size.height * (1 - (value - minValue) / range)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, Color(0xFFFFB74D), style = Stroke(width = 2.5.dp.toPx()))

        events.forEachIndexed { i, event ->
            val value = event.estimatedDepthMm.coerceIn(minValue, maxValue)
            val x = i * xStep
            val y = size.height * (1 - (value - minValue) / range)
            val inBand = value in targetMinMm..targetMaxMm
            drawCircle(
                color = if (inBand) Color(0xFFFF9800) else Color(0xFFF44336),
                radius = 2.dp.toPx(),
                center = Offset(x, y)
            )
        }
    }
}

private fun DrawScope.drawDashedLine(y: Float, color: Color) {
    val dashWidth = 8.dp.toPx()
    val gapWidth = 4.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(
            color = color,
            start = Offset(x, y),
            end = Offset((x + dashWidth).coerceAtMost(size.width), y),
            strokeWidth = 1.dp.toPx()
        )
        x += dashWidth + gapWidth
    }
}
