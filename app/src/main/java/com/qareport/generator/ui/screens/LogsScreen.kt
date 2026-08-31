package com.qareport.generator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.ui.common.QaTextField
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.components.SectionCard
import com.qareport.generator.ui.viewmodel.LogsViewModel
import com.qareport.generator.utils.ShareUtils

/**
 * Registro de logs.
 *
 * El contenido lo aporta el usuario (pegándolo o adjuntando un archivo). La aplicación
 * NO ejecuta comandos ADB ni accede al logcat del sistema por su cuenta.
 */
@Composable
fun LogsScreen(
    viewModel: LogsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val draftTitle by viewModel.draftTitle.collectAsStateWithLifecycle()
    val draftContent by viewModel.draftContent.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    val pickLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::loadFromUri) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(title = "Nuevo log") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QaTextField(
                        value = draftTitle,
                        onValueChange = viewModel::onDraftTitleChange,
                        label = "Título",
                        placeholder = "Logcat - transacción CTLS"
                    )
                    QaTextField(
                        value = draftContent,
                        onValueChange = viewModel::onDraftContentChange,
                        label = "Pegar log",
                        singleLine = false,
                        minLines = 6,
                        placeholder = "08:42:12 INFO Start transaction"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pickLogLauncher.launch(arrayOf("text/*", "application/octet-stream")) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Adjuntar TXT") }
                        Button(
                            onClick = viewModel::saveDraft,
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f)
                        ) { Text("Guardar log") }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar en los logs") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )
        }

        if (logs.isEmpty()) {
            item {
                EmptyState(
                    title = "Sin logs guardados",
                    description = "Pega el contenido del log o adjunta un archivo de texto " +
                        "para incluirlo en el reporte."
                )
            }
        } else {
            items(logs, key = { it.id }) { entry ->
                LogCard(
                    entry = entry,
                    onCopy = { clipboard.setText(AnnotatedString(entry.content)) },
                    onExport = {
                        viewModel.exportToTxt(entry) { file ->
                            ShareUtils.shareFile(context, file, "text/plain", "Compartir log")
                        }
                    },
                    onDelete = { viewModel.delete(entry) }
                )
            }
        }
    }
}

@Composable
private fun LogCard(
    entry: LogEntry,
    onCopy: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = DateFormats.formatDateTime(entry.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar log",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // El log se muestra en monoespaciado y con scroll horizontal para no romper
            // las columnas de las trazas.
            Text(
                text = entry.content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .heightIn(max = 220.dp)
                    .horizontalScroll(rememberScrollState())
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onCopy) { Text("Copiar") }
                TextButton(onClick = onExport) { Text("Exportar TXT") }
            }
        }
    }
}
