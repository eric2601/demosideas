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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.viewmodel.ProjectListViewModel

/**
 * Pestaña "Pruebas" del menú inferior: los casos de prueba pertenecen siempre a un
 * proyecto, así que primero se elige el proyecto sobre el que trabajar.
 */
@Composable
fun TestsHubScreen(
    viewModel: ProjectListViewModel,
    onOpenTestCases: (Long) -> Unit,
    onOpenExecution: (Long) -> Unit,
    onCreateProject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()

    if (projects.isEmpty()) {
        EmptyState(
            title = "No hay proyectos",
            description = "Los casos de prueba pertenecen a un proyecto. Crea uno para empezar.",
            actionLabel = "Crear proyecto",
            onAction = onCreateProject,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Elige el proyecto sobre el que quieres trabajar",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(projects, key = { it.id }) { project ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = project.displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenTestCases(project.id) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Casos") }
                        OutlinedButton(
                            onClick = { onOpenExecution(project.id) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Ejecutar") }
                    }
                }
            }
        }
    }
}
