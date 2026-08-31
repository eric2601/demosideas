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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.ui.components.ColoredChip
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.viewmodel.ProjectListViewModel

@Composable
fun ProjectListScreen(
    viewModel: ProjectListViewModel,
    onOpenProject: (Long) -> Unit,
    onEditProject: (Long) -> Unit,
    onCreateProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var pendingDeletion by remember { mutableStateOf<Project?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Buscar proyecto") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )

        if (projects.isEmpty()) {
            EmptyState(
                title = if (query.isBlank()) "No hay proyectos" else "Sin resultados",
                description = if (query.isBlank()) {
                    "Crea un proyecto para empezar a registrar casos de prueba."
                } else {
                    "Ningún proyecto coincide con \"$query\"."
                },
                actionLabel = if (query.isBlank()) "Crear proyecto" else null,
                onAction = if (query.isBlank()) onCreateProject else null
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onClick = { onOpenProject(project.id) },
                        onEdit = { onEditProject(project.id) },
                        onDelete = { pendingDeletion = project }
                    )
                }
            }
        }
    }

    pendingDeletion?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Eliminar proyecto") },
            text = {
                Text(
                    "Se eliminarán también sus casos de prueba, ejecuciones, defectos, " +
                        "evidencias y reportes. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(project)
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
private fun ProjectCard(
    project: Project,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
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
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(
                        project.version.takeIf { it.isNotBlank() }?.let { "v$it" },
                        project.buildNumber.takeIf { it.isNotBlank() }?.let { "build $it" },
                        project.client.takeIf { it.isNotBlank() }
                    ).filterNotNull().joinToString(" · ").ifBlank { "Sin versión registrada" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (project.finalized) {
                        ColoredChip(
                            text = "FINALIZADO",
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                    Text(
                        text = DateFormats.formatDate(project.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar proyecto")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Eliminar proyecto",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
