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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.ui.components.ColoredChip
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.components.SeverityChip
import com.qareport.generator.ui.components.StatusChip
import com.qareport.generator.ui.viewmodel.TestCaseListViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestCaseListScreen(
    viewModel: TestCaseListViewModel,
    onCreateTestCase: () -> Unit,
    onEditTestCase: (Long) -> Unit,
    onOpenEvidence: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val cases by viewModel.testCases.collectAsStateWithLifecycle()
    val counts by viewModel.counts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    var pendingDeletion by remember { mutableStateOf<TestCase?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            label = { Text("Buscar por ID, título o módulo") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TestStatus.entries.forEach { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { viewModel.onStatusFilterChange(status) },
                    label = { Text("${status.label} ${counts[status] ?: 0}") }
                )
            }
        }

        if (cases.isEmpty()) {
            EmptyState(
                title = "Sin casos de prueba",
                description = "Agrega el primer caso de prueba para comenzar la ejecución.",
                actionLabel = "Nuevo caso",
                onAction = onCreateTestCase
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(cases, key = { it.id }) { testCase ->
                    TestCaseCard(
                        testCase = testCase,
                        onClick = { onEditTestCase(testCase.id) },
                        onEvidence = { onOpenEvidence(testCase.id) },
                        onDelete = { pendingDeletion = testCase }
                    )
                }
            }
        }
    }

    pendingDeletion?.let { testCase ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Eliminar ${testCase.code}") },
            text = { Text("Se eliminarán también sus ejecuciones y evidencias asociadas.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(testCase)
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
private fun TestCaseCard(
    testCase: TestCase,
    onClick: () -> Unit,
    onEvidence: () -> Unit,
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
                    text = testCase.code,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "  ${testCase.title}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar caso",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(testCase.status)
                SeverityChip(testCase.severity)
                if (testCase.module.isNotBlank()) {
                    ColoredChip(
                        text = testCase.module,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
                ColoredChip(
                    text = testCase.priority.label,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            TextButton(onClick = onEvidence) { Text("Evidencias") }
        }
    }
}
