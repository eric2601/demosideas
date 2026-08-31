package com.qareport.generator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.ui.common.ChipSelector
import com.qareport.generator.ui.components.BugStatusChip
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.components.SeverityChip
import com.qareport.generator.ui.viewmodel.BugViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BugListScreen(
    viewModel: BugViewModel,
    onCreateBug: () -> Unit,
    onEditBug: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val bugs by viewModel.bugs.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    var pendingDeletion by remember { mutableStateOf<Bug?>(null) }
    var statusEditing by remember { mutableStateOf<Bug?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BugStatus.entries.forEach { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { viewModel.onStatusFilterChange(status) },
                    label = { Text(status.label) }
                )
            }
        }

        if (bugs.isEmpty()) {
            EmptyState(
                title = "Sin defectos registrados",
                description = "Registra los defectos encontrados durante la ejecución de las pruebas.",
                actionLabel = "Nuevo defecto",
                onAction = onCreateBug
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(bugs, key = { it.id }) { bug ->
                    BugCard(
                        bug = bug,
                        onClick = { onEditBug(bug.id) },
                        onChangeStatus = { statusEditing = bug },
                        onDelete = { pendingDeletion = bug }
                    )
                }
            }
        }
    }

    statusEditing?.let { bug ->
        AlertDialog(
            onDismissRequest = { statusEditing = null },
            title = { Text("Estado de ${bug.code}") },
            text = {
                ChipSelector(
                    options = BugStatus.entries,
                    selected = bug.status,
                    onSelect = { status ->
                        viewModel.updateStatus(bug, status)
                        statusEditing = null
                    },
                    label = { it.label }
                )
            },
            confirmButton = {
                TextButton(onClick = { statusEditing = null }) { Text("Cerrar") }
            }
        )
    }

    pendingDeletion?.let { bug ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Eliminar ${bug.code}") },
            text = { Text("El defecto dejará de aparecer en el reporte. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(bug)
                        pendingDeletion = null
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun BugCard(
    bug: Bug,
    onClick: () -> Unit,
    onChangeStatus: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = bug.code,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "  ${bug.title}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar defecto",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeverityChip(bug.severity)
                BugStatusChip(bug.status)
                Text(
                    text = DateFormats.formatDate(bug.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onChangeStatus) { Text("Cambiar estado") }
        }
    }
}
