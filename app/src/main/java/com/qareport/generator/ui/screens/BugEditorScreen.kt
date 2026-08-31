package com.qareport.generator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.ui.common.ChipSelector
import com.qareport.generator.ui.common.QaTextField
import com.qareport.generator.ui.components.SectionCard
import com.qareport.generator.ui.viewmodel.BugEditorViewModel

@Composable
fun BugEditorScreen(
    viewModel: BugEditorViewModel,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = state.code.ifBlank { "Nuevo defecto" }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QaTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = "Título",
                    required = true,
                    error = state.errors[Validators.Fields.BUG_TITLE],
                    placeholder = "Terminal congelada durante CTLS"
                )
                QaTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = "Descripción",
                    required = true,
                    error = state.errors[Validators.Fields.BUG_DESCRIPTION],
                    singleLine = false,
                    minLines = 3
                )
            }
        }

        SectionCard(title = "Clasificación") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Severidad", style = MaterialTheme.typography.labelMedium)
                ChipSelector(
                    options = Severity.entries,
                    selected = state.severity,
                    onSelect = viewModel::onSeverityChange,
                    label = { it.label }
                )
                Text("Prioridad", style = MaterialTheme.typography.labelMedium)
                ChipSelector(
                    options = Priority.entries,
                    selected = state.priority,
                    onSelect = viewModel::onPriorityChange,
                    label = { it.label }
                )
                Text("Estado", style = MaterialTheme.typography.labelMedium)
                ChipSelector(
                    options = BugStatus.entries,
                    selected = state.status,
                    onSelect = viewModel::onStatusChange,
                    label = { it.label }
                )
            }
        }

        SectionCard(title = "Reproducción") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QaTextField(
                    value = state.stepsToReproduce,
                    onValueChange = viewModel::onStepsChange,
                    label = "Pasos para reproducir (uno por línea)",
                    required = true,
                    error = state.errors[Validators.Fields.BUG_STEPS],
                    singleLine = false,
                    minLines = 4
                )
                QaTextField(
                    value = state.expectedResult,
                    onValueChange = viewModel::onExpectedChange,
                    label = "Resultado esperado",
                    singleLine = false,
                    minLines = 2
                )
                QaTextField(
                    value = state.actualResult,
                    onValueChange = viewModel::onActualChange,
                    label = "Resultado actual",
                    singleLine = false,
                    minLines = 2
                )
            }
        }

        SectionCard(title = "Contexto") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QaTextField(
                    value = state.version,
                    onValueChange = viewModel::onVersionChange,
                    label = "Versión",
                    capitalization = KeyboardCapitalization.None,
                    modifier = Modifier.weight(1f)
                )
                QaTextField(
                    value = state.device,
                    onValueChange = viewModel::onDeviceChange,
                    label = "Dispositivo",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) { Text("Cancelar") }
            Button(
                onClick = { viewModel.save(onSaved) },
                modifier = Modifier.weight(1f),
                enabled = !state.isSaving,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) { Text(if (state.isEditing) "Guardar" else "Registrar defecto") }
        }
    }
}
