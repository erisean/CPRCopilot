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
import com.hackathon.cprwatch.shared.CprDataPoint

@Composable
fun RateChart(
    dataPoints: List<CprDataPoint>,
    modifier: Modifier = Modifier
) {
    ChartWithBands(
        title = "Compression Rate (BPM)",
        dataPoints = dataPoints,
        valueSelector = { it.rate.toFloat() },
        minValue = 60f,
        maxValue = 160f,
        bandLow = 100f,
        bandHigh = 120f,
        lineColor = Color(0xFF2196F3),
        bandColor = Color(0x2000C853),
        modifier = modifier
    )
}

@Composable
fun DepthChart(
    dataPoints: List<CprDataPoint>,
    modifier: Modifier = Modifier
) {
    ChartWithBands(
        title = "Compression Depth (cm)",
        dataPoints = dataPoints,
        valueSelector = { it.depthCm },
        minValue = 0f,
        maxValue = 10f,
        bandLow = 5f,
        bandHigh = 6f,
        lineColor = Color(0xFFFF9800),
        bandColor = Color(0x2000C853),
        modifier = modifier
    )
}

@Composable
private fun ChartWithBands(
    title: String,
    dataPoints: List<CprDataPoint>,
    valueSelector: (CprDataPoint) -> Float,
    minValue: Float,
    maxValue: Float,
    bandLow: Float,
    bandHigh: Float,
    lineColor: Color,
    bandColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(top = 8.dp)
        ) {
            if (dataPoints.isEmpty()) return@Canvas

            val range = maxValue - minValue
            val bandTopY = size.height * (1 - (bandHigh - minValue) / range)
            val bandBottomY = size.height * (1 - (bandLow - minValue) / range)

            // Target band
            drawRect(
                color = bandColor,
                topLeft = Offset(0f, bandTopY),
                size = androidx.compose.ui.geometry.Size(size.width, bandBottomY - bandTopY)
            )

            // Band boundary lines
            drawDashedLine(bandTopY, Color.Green.copy(alpha = 0.5f))
            drawDashedLine(bandBottomY, Color.Green.copy(alpha = 0.5f))

            // Data line
            if (dataPoints.size < 2) return@Canvas

            val path = Path()
            val xStep = size.width / (dataPoints.size - 1).coerceAtLeast(1)

            dataPoints.forEachIndexed { index, point ->
                val value = valueSelector(point).coerceIn(minValue, maxValue)
                val x = index * xStep
                val y = size.height * (1 - (value - minValue) / range)

                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx())
            )

            // Draw dots for each point
            dataPoints.forEachIndexed { index, point ->
                val value = valueSelector(point).coerceIn(minValue, maxValue)
                val x = index * xStep
                val y = size.height * (1 - (value - minValue) / range)

                val inBand = value in bandLow..bandHigh
                drawCircle(
                    color = if (inBand) Color.Green else Color.Red,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y)
                )
            }
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
