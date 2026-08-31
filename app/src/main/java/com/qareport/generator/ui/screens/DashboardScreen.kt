package com.qareport.generator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.components.StatCard
import com.qareport.generator.ui.theme.QaColors
import com.qareport.generator.ui.viewmodel.DashboardViewModel

/**
 * Pantalla principal: métricas globales, acceso rápido a crear proyecto y
 * lista de proyectos recientes.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onCreateProject: () -> Unit,
    onOpenProject: (Long) -> Unit,
    onSeeAllProjects: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.globalStats.collectAsStateWithLifecycle()
    val recent by viewModel.recentProjects.collectAsStateWithLifecycle()
    val isLoadingDemo by viewModel.isLoadingDemo.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "QA REPORT GENERATOR",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Resumen general",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Proyectos",
                    value = stats.projects.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Pruebas ejecutadas",
                    value = stats.executedTests.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "PASS",
                    value = stats.passed.toString(),
                    accent = QaColors.status.pass,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "FAIL",
                    value = stats.failed.toString(),
                    accent = QaColors.status.fail,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Button(
                onClick = onCreateProject,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  NUEVO PROYECTO")
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.loadDemoData(onLoaded = onOpenProject) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingDemo
            ) {
                if (isLoadingDemo) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Science, contentDescription = null)
                }
                Text("  Cargar datos de demostración")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Proyectos recientes", style = MaterialTheme.typography.titleMedium)
                if (recent.isNotEmpty()) {
                    OutlinedButton(onClick = onSeeAllProjects) { Text("Ver todos") }
                }
            }
        }

        if (recent.isEmpty()) {
            item {
                EmptyState(
                    title = "Aún no hay proyectos",
                    description = "Crea tu primer proyecto QA o carga los datos de demostración " +
                        "para ver cómo funciona la aplicación.",
                    actionLabel = "Crear proyecto",
                    onAction = onCreateProject
                )
            }
        } else {
            items(recent, key = { it.id }) { project ->
                RecentProjectCard(project = project, onClick = { onOpenProject(project.id) })
            }
        }
    }
}

@Composable
private fun RecentProjectCard(project: Project, onClick: () -> Unit) {
    Card(
        onClick = onClick,
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
            Text(
                text = listOf(project.client, project.deviceModel, project.environment)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "Sin información adicional" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Actualizado: ${DateFormats.formatDateTime(project.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
