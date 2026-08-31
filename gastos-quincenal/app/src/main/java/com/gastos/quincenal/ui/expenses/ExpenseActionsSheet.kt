package com.gastos.quincenal.ui.expenses

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gastos.quincenal.domain.model.ExpenseOccurrence
import com.gastos.quincenal.ui.components.ConfirmDialog
import com.gastos.quincenal.utils.DateUtils

/**
 * Acciones disponibles sobre un gasto: editar, duplicar, marcar pagado y borrar.
 *
 * En un gasto recurrente se distingue entre quitar solo esta fecha y eliminar la
 * serie completa, y ambas eliminaciones piden confirmacion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseActionsSheet(
    occurrence: ExpenseOccurrence,
    onDismiss: () -> Unit,
    onTogglePaid: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDeleteOccurrence: () -> Unit,
    onDeleteSeries: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var pendingDeletion by remember { mutableStateOf<Deletion?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = occurrence.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${DateUtils.formatLong(occurrence.date)} - ${occurrence.quincena.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            occurrence.recurrence?.let { rule ->
                Text(
                    text = rule.describe(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            SheetAction(
                icon = Icons.Filled.CheckCircle,
                label = if (occurrence.isPaid) "Marcar como pendiente" else "Marcar como pagado",
                onClick = onTogglePaid
            )
            SheetAction(icon = Icons.Filled.Edit, label = "Editar gasto", onClick = onEdit)
            SheetAction(icon = Icons.Filled.ContentCopy, label = "Duplicar gasto", onClick = onDuplicate)

            if (occurrence.isRecurring) {
                SheetAction(
                    icon = Icons.Filled.Delete,
                    label = "Quitar solo esta fecha",
                    onClick = { pendingDeletion = Deletion.OCCURRENCE }
                )
                SheetAction(
                    icon = Icons.Filled.DeleteSweep,
                    label = "Eliminar toda la serie",
                    onClick = { pendingDeletion = Deletion.SERIES }
                )
            } else {
                SheetAction(
                    icon = Icons.Filled.Delete,
                    label = "Eliminar gasto",
                    onClick = { pendingDeletion = Deletion.SERIES }
                )
            }
        }
    }

    when (pendingDeletion) {
        Deletion.OCCURRENCE -> ConfirmDialog(
            title = "Quitar esta fecha",
            message = "Se quitara \"${occurrence.name}\" del " +
                "${DateUtils.formatLong(occurrence.date)}. El resto de las repeticiones no cambia.",
            confirmText = "Quitar",
            onConfirm = {
                pendingDeletion = null
                onDeleteOccurrence()
            },
            onDismiss = { pendingDeletion = null }
        )

        Deletion.SERIES -> ConfirmDialog(
            title = "Eliminar gasto",
            message = if (occurrence.isRecurring) {
                "Se eliminara \"${occurrence.name}\" en todos los meses. Esta accion no se puede deshacer."
            } else {
                "Se eliminara \"${occurrence.name}\". Esta accion no se puede deshacer."
            },
            confirmText = "Eliminar",
            onConfirm = {
                pendingDeletion = null
                onDeleteSeries()
            },
            onDismiss = { pendingDeletion = null }
        )

        null -> Unit
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(imageVector = icon, contentDescription = null)
        Text(text = "  $label", modifier = Modifier.fillMaxWidth())
    }
}

private enum class Deletion { OCCURRENCE, SERIES }
