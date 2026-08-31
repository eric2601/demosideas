package com.qareport.generator.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.qareport.generator.domain.model.Evidence
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.util.TextUtils
import com.qareport.generator.ui.components.ColoredChip
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.components.LoadingOverlay
import com.qareport.generator.ui.viewmodel.EvidenceViewModel
import com.qareport.generator.utils.ShareUtils

/**
 * Gestión de evidencias: foto, imagen de galería o cualquier archivo mediante el
 * Storage Access Framework. El archivo se copia al almacenamiento privado de la app.
 */
@Composable
fun EvidenceScreen(
    viewModel: EvidenceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val evidences by viewModel.evidences.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    var pendingDeletion by remember { mutableStateOf<Evidence?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success -> viewModel.onCameraResult(success) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.prepareCameraCapture()?.let(takePictureLauncher::launch)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::attachFromUri) }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::attachFromUri) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        // Se pide el permiso justo antes de usar la cámara, no al abrir la app.
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text("  TOMAR FOTO")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pickImageLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Text("  Imagen")
                    }
                    OutlinedButton(
                        onClick = { pickFileLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AttachFile, contentDescription = null)
                        Text("  Archivo")
                    }
                }
                Text(
                    text = "Las evidencias se copian al almacenamiento privado de la aplicación " +
                        "(máximo ${TextUtils.formatBytes(com.qareport.generator.utils.FileStorage.MAX_EVIDENCE_BYTES)} por archivo).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (evidences.isEmpty()) {
                EmptyState(
                    title = "Sin evidencias",
                    description = "Agrega fotografías, capturas de pantalla, logs o cualquier archivo " +
                        "que respalde el resultado de las pruebas."
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(evidences, key = { it.id }) { evidence ->
                        EvidenceCard(
                            evidence = evidence,
                            onOpen = {
                                val file = viewModel.fileFor(evidence)
                                if (file == null) {
                                    viewModel.delete(evidence)
                                } else {
                                    ShareUtils.openFile(
                                        context,
                                        file,
                                        evidence.mimeType.ifBlank { "*/*" }
                                    )
                                }
                            },
                            onShare = {
                                viewModel.fileFor(evidence)?.let { file ->
                                    ShareUtils.shareFile(
                                        context,
                                        file,
                                        evidence.mimeType.ifBlank { "*/*" },
                                        "Compartir evidencia"
                                    )
                                }
                            },
                            onDelete = { pendingDeletion = evidence }
                        )
                    }
                }
            }
        }

        if (isBusy) {
            LoadingOverlay(message = "Guardando evidencia…", modifier = Modifier.fillMaxSize())
        }
    }

    pendingDeletion?.let { evidence ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Eliminar evidencia") },
            text = { Text("Se eliminará \"${evidence.displayName}\" del dispositivo y del reporte.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(evidence)
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
private fun EvidenceCard(
    evidence: Evidence,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = evidence.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColoredChip(
                        text = evidence.type.label,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${TextUtils.formatBytes(evidence.sizeBytes)} · " +
                            DateFormats.formatDateTime(evidence.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onShare) { Text("Compartir") }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Eliminar evidencia",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
