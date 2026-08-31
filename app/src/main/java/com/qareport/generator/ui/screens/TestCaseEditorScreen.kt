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
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.ui.common.ChipSelector
import com.qareport.generator.ui.common.QaTextField
import com.qareport.generator.ui.components.SectionCard
import com.qareport.generator.ui.viewmodel.TestCaseEditorViewModel

@Composable
fun TestCaseEditorScreen(
    viewModel: TestCaseEditorViewModel,
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
        SectionCard(title = "Identificación") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QaTextField(
                        value = state.code,
                        onValueChange = viewModel::onCodeChange,
                        label = "ID",
                        required = true,
                        error = state.errors[Validators.Fields.TEST_CODE],
                        placeholder = "TC-001",
                        capitalization = KeyboardCapitalization.Characters,
                        modifier = Modifier.weight(1f)
                    )
                    QaTextField(
                        value = state.module,
                        onValueChange = viewModel::onModuleChange,
                        label = "Módulo",
                        placeholder = "Instalación",
                        modifier = Modifier.weight(1.4f)
                    )
                }
                QaTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    label = "Título",
                    required = true,
                    error = state.errors[Validators.Fields.TEST_TITLE],
                    placeholder = "Instalación del APK"
                )
                Text("Prioridad", style = MaterialTheme.typography.labelMedium)
                ChipSelector(
                    options = Priority.entries,
                    selected = state.priority,
                    onSelect = viewModel::onPriorityChange,
                    label = { it.label }
                )
            }
        }

        SectionCard(title = "Definición del caso") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QaTextField(
                    value = state.preconditions,
                    onValueChange = viewModel::onPreconditionsChange,
                    label = "Precondiciones",
                    singleLine = false,
                    minLines = 2
                )
                QaTextField(
                    value = state.steps,
                    onValueChange = viewModel::onStepsChange,
                    label = "Pasos (uno por línea)",
                    singleLine = false,
                    minLines = 4,
                    placeholder = "1. Conectar dispositivo."
                )
                QaTextField(
                    value = state.expectedResult,
                    onValueChange = viewModel::onExpectedChange,
                    label = "Resultado esperado",
                    required = true,
                    error = state.errors[Validators.Fields.TEST_EXPECTED],
                    singleLine = false,
                    minLines = 2
                )
            }
        }

        SectionCard(title = "Resultado") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Estado", style = MaterialTheme.typography.labelMedium)
                ChipSelector(
                    options = TestStatus.entries,
                    selected = state.status,
                    onSelect = viewModel::onStatusChange,
                    label = { it.label }
                )

                if (state.status == TestStatus.FAIL || state.status == TestStatus.BLOCKED) {
                    Text("Severidad", style = MaterialTheme.typography.labelMedium)
                    ChipSelector(
                        options = Severity.entries,
                        selected = state.severity,
                        onSelect = { viewModel.onSeverityChange(it) },
                        label = { it.label }
                    )
                }

                QaTextField(
                    value = state.actualResult,
                    onValueChange = viewModel::onActualChange,
                    label = "Resultado obtenido",
                    singleLine = false,
                    minLines = 2
                )
                QaTextField(
                    value = state.comments,
                    onValueChange = viewModel::onCommentsChange,
                    label = "Comentarios",
                    singleLine = false,
                    minLines = 2
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
            ) { Text(if (state.isEditing) "Guardar" else "Crear caso") }
        }
    }
}
