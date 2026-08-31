package com.qareport.generator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.ui.common.ChipSelector
import com.qareport.generator.ui.common.QaTextField
import com.qareport.generator.ui.components.EmptyState
import com.qareport.generator.ui.components.LabeledValue
import com.qareport.generator.ui.components.ProgressBarQa
import com.qareport.generator.ui.components.SectionCard
import com.qareport.generator.ui.components.StatusChip
import com.qareport.generator.ui.theme.QaColors
import com.qareport.generator.ui.viewmodel.TestExecutionViewModel

/**
 * Ejecución secuencial de los casos de prueba con botones grandes de resultado.
 * FAIL abre el formulario obligatorio de diagnóstico antes de poder registrarse.
 */
@Composable
fun ExecutionScreen(
    viewModel: TestExecutionViewModel,
    onAddEvidence: (Long) -> Unit,
    onNoCases: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val failForm by viewModel.failForm.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var showFailForm by remember { mutableStateOf(false) }
    val statusColors = QaColors.status

    val currentCase = state.currentCase
    if (currentCase == null) {
        EmptyState(
            title = "No hay casos que ejecutar",
            description = "Agrega casos de prueba al proyecto para poder ejecutarlos.",
            actionLabel = "Ir a casos de prueba",
            onAction = onNoCases,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard(title = "Progreso") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressBarQa(executed = state.stats.executed, total = state.stats.total)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CountLabel("PASS", state.stats.passed, statusColors.pass)
                    CountLabel("FAIL", state.stats.failed, statusColors.fail)
                    CountLabel("BLOCKED", state.stats.blocked, statusColors.blocked)
                    CountLabel("SKIPPED", state.stats.skipped, statusColors.skipped)
                    CountLabel("PENDING", state.stats.pending, statusColors.pending)
                }
            }
        }

        SectionCard(
            title = "${currentCase.code} (${state.position} de ${state.cases.size})",
            action = { StatusChip(currentCase.status) }
        ) {
            Column {
                Text(currentCase.title, style = MaterialTheme.typography.titleMedium)
                LabeledValue("Módulo", currentCase.module)
                LabeledValue("Prioridad", currentCase.priority.label)
                LabeledValue("Precondiciones", currentCase.preconditions)
                LabeledValue("Pasos", currentCase.steps)
                LabeledValue("Resultado esperado", currentCase.expectedResult)
                TextButton(onClick = { onAddEvidence(currentCase.id) }) {
                    Text("Agregar evidencia a este caso")
                }
            }
        }

        SectionCard(title = "Notas de la ejecución") {
            QaTextField(
                value = notes,
                onValueChange = viewModel::onNotesChange,
                label = "Observaciones",
                singleLine = false,
                minLines = 2
            )
        }

        if (!showFailForm) {
            SectionCard(title = "Registrar resultado") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BigResultButton(
                        label = "PASS",
                        color = statusColors.pass,
                        enabled = !state.isSaving
                    ) { viewModel.recordSimpleResult(TestStatus.PASS) }

                    BigResultButton(
                        label = "FAIL",
                        color = statusColors.fail,
                        enabled = !state.isSaving
                    ) { showFailForm = true }

                    BigResultButton(
                        label = "BLOCKED",
                        color = statusColors.blocked,
                        enabled = !state.isSaving
                    ) { viewModel.recordSimpleResult(TestStatus.BLOCKED) }

                    BigResultButton(
                        label = "SKIPPED",
                        color = statusColors.skipped,
                        enabled = !state.isSaving
                    ) { viewModel.recordSimpleResult(TestStatus.SKIPPED) }
                }
            }
        } else {
            SectionCard(title = "Detalle del fallo (obligatorio)") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    QaTextField(
                        value = failForm.problemDescription,
                        onValueChange = { value ->
                            viewModel.onFailFieldChange { copy(problemDescription = value) }
                        },
                        label = "Descripción del problema",
                        required = true,
                        error = failForm.errors[Validators.Fields.FAIL_DESCRIPTION],
                        singleLine = false,
                        minLines = 2
                    )

                    Text("Severidad *", style = MaterialTheme.typography.labelMedium)
                    ChipSelector(
                        options = Severity.entries,
                        selected = failForm.severity,
                        onSelect = { value -> viewModel.onFailFieldChange { copy(severity = value) } },
                        label = { it.label }
                    )
                    failForm.errors[Validators.Fields.FAIL_SEVERITY]?.let { error ->
                        Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }

                    QaTextField(
                        value = failForm.stepsToReproduce,
                        onValueChange = { value ->
                            viewModel.onFailFieldChange { copy(stepsToReproduce = value) }
                        },
                        label = "Pasos para reproducir",
                        required = true,
                        error = failForm.errors[Validators.Fields.FAIL_STEPS],
                        singleLine = false,
                        minLines = 3
                    )
                    QaTextField(
                        value = failForm.expectedResult,
                        onValueChange = { value ->
                            viewModel.onFailFieldChange { copy(expectedResult = value) }
                        },
                        label = "Resultado esperado",
                        required = true,
                        error = failForm.errors[Validators.Fields.FAIL_EXPECTED],
                        singleLine = false,
                        minLines = 2
                    )
                    QaTextField(
                        value = failForm.actualResult,
                        onValueChange = { value ->
                            viewModel.onFailFieldChange { copy(actualResult = value) }
                        },
                        label = "Resultado actual",
                        required = true,
                        error = failForm.errors[Validators.Fields.FAIL_ACTUAL],
                        singleLine = false,
                        minLines = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showFailForm = false
                                viewModel.resetFailForm()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancelar") }

                        Button(
                            onClick = {
                                viewModel.recordFail {
                                    showFailForm = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !state.isSaving,
                            colors = ButtonDefaults.buttonColors(containerColor = statusColors.fail)
                        ) { Text("Registrar FAIL") }
                    }

                    TextButton(
                        onClick = {
                            viewModel.onFailFieldChange { copy(createBug = !createBug) }
                        }
                    ) {
                        Text(
                            if (failForm.createBug) {
                                "Se creará un defecto automáticamente (tocar para desactivar)"
                            } else {
                                "No se creará defecto (tocar para activar)"
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::goToPrevious,
                enabled = state.hasPrevious,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text(" Anterior")
            }
            OutlinedButton(
                onClick = viewModel::goToNext,
                enabled = state.hasNext,
                modifier = Modifier.weight(1f)
            ) {
                Text("Siguiente ")
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
private fun CountLabel(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigResultButton(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}
