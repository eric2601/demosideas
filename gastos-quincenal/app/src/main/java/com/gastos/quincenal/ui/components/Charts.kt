package com.gastos.quincenal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.utils.CurrencyFormatter
import kotlin.math.abs

/** Una barra de las graficas: etiqueta, importe y color. */
data class BarDatum(
    val label: String,
    val value: Money,
    val color: Color,
    val caption: String? = null
)

/**
 * Grafica de barras horizontales dibujada con composables simples.
 *
 * Se resuelve sin librerias de terceros: la escala se calcula sobre el valor
 * absoluto mayor, de modo que los saldos negativos tambien se representan.
 */
@Composable
fun HorizontalBarChart(
    data: List<BarDatum>,
    formatter: CurrencyFormatter,
    modifier: Modifier = Modifier,
    barHeight: Int = 22
) {
    if (data.isEmpty()) {
        EmptyState(
            title = "Sin datos",
            message = "Registra movimientos para ver esta grafica.",
            modifier = modifier
        )
        return
    }

    val maxValue = data.maxOf { abs(it.value.cents) }.coerceAtLeast(1L)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        data.forEach { datum ->
            val fraction = (abs(datum.value.cents).toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = datum.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = formatter.format(datum.value),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = datum.color
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(barHeight.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(datum.color)
                    )
                }

                if (datum.caption != null) {
                    Text(
                        text = datum.caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Grafica de columnas verticales, util para comparar semanas del mes.
 * Los valores negativos se pintan hacia abajo respecto a la linea de cero.
 */
@Composable
fun VerticalBarChart(
    data: List<BarDatum>,
    formatter: CurrencyFormatter,
    modifier: Modifier = Modifier,
    chartHeight: Int = 140
) {
    if (data.isEmpty()) {
        EmptyState(
            title = "Sin datos",
            message = "Registra movimientos para ver esta grafica.",
            modifier = modifier
        )
        return
    }

    val maxValue = data.maxOf { abs(it.value.cents) }.coerceAtLeast(1L)
    val hasNegative = data.any { it.value.isNegative }
    val positiveHeight = if (hasNegative) chartHeight * 0.6f else chartHeight.toFloat()
    val negativeHeight = if (hasNegative) chartHeight * 0.4f else 0f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            data.forEach { datum ->
                val fraction = (abs(datum.value.cents).toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
                val isNegative = datum.value.isNegative
                val barSize = if (isNegative) negativeHeight * fraction else positiveHeight * fraction

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Box(
                        modifier = Modifier
                            .height(positiveHeight.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (!isNegative) {
                            Box(
                                modifier = Modifier
                                    .width(26.dp)
                                    .height(barSize.dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(datum.color)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline)
                    )

                    if (hasNegative) {
                        Box(
                            modifier = Modifier
                                .height(negativeHeight.dp)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            if (isNegative) {
                                Box(
                                    modifier = Modifier
                                        .width(26.dp)
                                        .height(barSize.dp.coerceAtLeast(2.dp))
                                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                                        .background(datum.color)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.forEach { datum ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = datum.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatter.formatCompact(datum.value),
                        style = MaterialTheme.typography.labelSmall,
                        color = datum.color
                    )
                }
            }
        }
    }
}
