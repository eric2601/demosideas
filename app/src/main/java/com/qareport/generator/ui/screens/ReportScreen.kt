package com.qareport.generator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.Report
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.util.TextUtils
import com.qareport.generator.ui.components.ColoredChip
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.viewmodel.ReportViewModel
import com.qareport.generator.utils.ShareUtils

/**
 * Historial de reportes y exportaciones: abrir, compartir, renombrar y eliminar.
 */
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    var pendingDeletion by remember { mutableStateOf<Report?>(null) }
    var renaming by remember { mutableStateOf<Report?>(null) }

    if (reports.isEmpty()) {
        EmptyState(
            title = "Aún no hay reportes",
            description = "Genera un reporte QA desde el detalle de un proyecto y aparecerá aquí.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(reports, key = { it.id }) { report ->
            ReportCard(
                report = report,
                onOpen = {
                    val file = viewModel.fileFor(report)
                    when {
                        file == null -> viewModel.reportMissing()
                        !ShareUtils.openFile(context, file, report.format.mimeType) ->
                            viewModel.noAppAvailable()
                    }
                },
                onShare = {
                    val file = viewModel.fileFor(report)
                    when {
                        file == null -> viewModel.reportMissing()
                        !ShareUtils.shareReport(context, file, report.format, "Compartir reporte QA") ->
                            viewModel.noAppAvailable()
                    }
                },
                onRename = { renaming = report },
                onDelete = { pendingDeletion = report }
            )
        }
    }

    renaming?.let { report ->
        var newName by remember(report.id) { mutableStateOf(report.fileName) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renombrar reporte") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Nombre del archivo") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.rename(report, newName)
                        renaming = null
                    }
                ) { Text("Renombrar") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("Cancelar") }
            }
        )
    }

    pendingDeletion?.let { report ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Eliminar reporte") },
            text = { Text("Se eliminará el archivo \"${report.fileName}\" del dispositivo.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(report)
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
private fun ReportCard(
    report: Report,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = report.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = DateFormats.formatDateTime(report.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar reporte",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColoredChip(
                    text = report.format.label,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
                Text(
                    text = "${TextUtils.formatBytes(report.sizeBytes)} · " +
                        "${report.totalTests} casos · " +
                        TextUtils.formatPercent(report.passRate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onOpen) { Text("Abrir") }
                TextButton(onClick = onShare) { Text("Compartir") }
                TextButton(onClick = onRename) { Text("Renombrar") }
            }
        }
    }
}
