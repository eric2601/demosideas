package com.qareport.generator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Gráficas dibujadas con [Canvas] de Compose.
 *
 * No se usa ninguna librería de gráficas: las necesidades son sencillas (dona, barras
 * y línea) y evitarlas mantiene el APK pequeño y elimina un riesgo de compatibilidad.
 */

/** Segmento genérico de una gráfica. */
data class ChartSlice(
    val label: String,
    val value: Int,
    val color: Color
)

/** Dona de distribución con el total en el centro. */
@Composable
fun DonutChart(
    slices: List<ChartSlice>,
    centerValue: String,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.value }
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.size(132.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(132.dp)) {
                val strokeWidth = size.minDimension * 0.18f
                val inset = strokeWidth / 2f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(inset, inset)

                if (total <= 0) {
                    drawArc(
                        color = emptyColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    return@Canvas
                }

                var startAngle = -90f
                slices.forEach { slice ->
                    if (slice.value <= 0) return@forEach
                    val sweep = 360f * slice.value / total
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    startAngle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = centerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawCircle(color = slice.color)
                    }
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = slice.value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Barras horizontales; útil para "bugs por severidad" y "pruebas por módulo". */
@Composable
fun HorizontalBarChart(
    slices: List<ChartSlice>,
    modifier: Modifier = Modifier
) {
    val maxValue = slices.maxOfOrNull { it.value } ?: 0
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        slices.forEach { slice ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = slice.value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(top = 2.dp)
                ) {
                    drawRoundRect(
                        color = trackColor,
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    )
                    if (maxValue > 0 && slice.value > 0) {
                        drawRoundRect(
                            color = slice.color,
                            size = Size(size.width * slice.value / maxValue, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                        )
                    }
                }
            }
        }
    }
}

/** Punto de la gráfica de evolución. */
data class TrendPoint(val label: String, val value: Int)

/** Línea de evolución de la ejecución de pruebas por día. */
@Composable
fun LineChart(
    points: List<TrendPoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    val gridColor = MaterialTheme.colorScheme.surfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val maxValue = points.maxOfOrNull { it.value }?.coerceAtLeast(1) ?: 1

            // Rejilla horizontal de referencia (4 divisiones).
            repeat(5) { index ->
                val y = size.height * index / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            if (points.size < 2) {
                if (points.size == 1) {
                    val y = size.height - size.height * points[0].value / maxValue
                    drawCircle(color = lineColor, radius = 5f, center = Offset(size.width / 2f, y))
                }
                return@Canvas
            }

            val stepX = size.width / (points.size - 1).toFloat()
            val path = Path()
            points.forEachIndexed { index, point ->
                val x = stepX * index
                val y = size.height - (size.height * point.value / maxValue)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 4f))

            points.forEachIndexed { index, point ->
                val x = stepX * index
                val y = size.height - (size.height * point.value / maxValue)
                drawCircle(color = lineColor, radius = 5f, center = Offset(x, y))
            }
        }

        if (points.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(points.first().label, style = MaterialTheme.typography.labelSmall)
                if (points.size > 1) {
                    Text(points.last().label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
