package com.qareport.generator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.ui.theme.QaColors

/** Tarjeta de métrica del Dashboard: etiqueta arriba, número grande abajo. */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            if (onClick != null) {
                TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Ver detalle", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Bloque de sección con título y contenido, usado en todas las pantallas de detalle. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/** Etiqueta de estado de un caso de prueba. */
@Composable
fun StatusChip(status: TestStatus, modifier: Modifier = Modifier) {
    ColoredChip(
        text = status.label,
        contentColor = QaColors.status.colorFor(status),
        containerColor = QaColors.status.containerFor(status),
        modifier = modifier
    )
}

@Composable
fun SeverityChip(severity: Severity?, modifier: Modifier = Modifier) {
    if (severity == null) return
    val color = QaColors.status.colorFor(severity)
    ColoredChip(
        text = severity.label,
        contentColor = color,
        containerColor = color.copy(alpha = 0.14f),
        modifier = modifier
    )
}

@Composable
fun BugStatusChip(status: BugStatus, modifier: Modifier = Modifier) {
    val color = QaColors.status.colorFor(status)
    ColoredChip(
        text = status.label,
        contentColor = color,
        containerColor = color.copy(alpha = 0.14f),
        modifier = modifier
    )
}

@Composable
fun ColoredChip(
    text: String,
    contentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** Estado vacío con mensaje y acción opcional. */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Barra de progreso con etiqueta "ejecutadas / total". */
@Composable
fun ProgressBarQa(
    executed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val fraction = if (total <= 0) 0f else (executed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Pruebas: $executed / $total", style = MaterialTheme.typography.labelLarge)
            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/** Fila etiqueta/valor usada en las pantallas de detalle. */
@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    if (value.isBlank()) return
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Superposición de carga que bloquea la interacción durante una operación larga. */
@Composable
fun LoadingOverlay(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
