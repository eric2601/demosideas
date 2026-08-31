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
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.ui.common.QaTextField
import com.qareport.generator.ui.components.SectionCard
import com.qareport.generator.ui.viewmodel.ProjectEditorViewModel
import com.qareport.generator.ui.viewmodel.ProjectField

/**
 * Formulario de creación y edición de proyecto.
 * Los campos obligatorios se marcan con asterisco y se validan al guardar.
 */
@Composable
fun ProjectEditorScreen(
    viewModel: ProjectEditorViewModel,
    onSaved: (Long) -> Unit,
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
                QaTextField(
                    value = state.name,
                    onValueChange = { viewModel.onFieldChange(ProjectField.NAME, it) },
                    label = "Nombre del proyecto",
                    required = true,
                    error = state.errors[Validators.Fields.PROJECT_NAME],
                    placeholder = "Terminal P630 QA"
                )
                QaTextField(
                    value = state.client,
                    onValueChange = { viewModel.onFieldChange(ProjectField.CLIENT, it) },
                    label = "Cliente",
                    placeholder = "Empresa XYZ"
                )
                QaTextField(
                    value = state.product,
                    onValueChange = { viewModel.onFieldChange(ProjectField.PRODUCT, it) },
                    label = "Producto"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QaTextField(
                        value = state.version,
                        onValueChange = { viewModel.onFieldChange(ProjectField.VERSION, it) },
                        label = "Versión",
                        required = true,
                        error = state.errors[Validators.Fields.PROJECT_VERSION],
                        placeholder = "1.2.8",
                        capitalization = KeyboardCapitalization.None,
                        modifier = Modifier.weight(1f)
                    )
                    QaTextField(
                        value = state.buildNumber,
                        onValueChange = { viewModel.onFieldChange(ProjectField.BUILD, it) },
                        label = "Build",
                        placeholder = "128",
                        capitalization = KeyboardCapitalization.None,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionCard(title = "Dispositivo y ambiente") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QaTextField(
                    value = state.deviceType,
                    onValueChange = { viewModel.onFieldChange(ProjectField.DEVICE_TYPE, it) },
                    label = "Tipo de dispositivo",
                    placeholder = "Terminal de pago"
                )
                QaTextField(
                    value = state.deviceModel,
                    onValueChange = { viewModel.onFieldChange(ProjectField.DEVICE_MODEL, it) },
                    label = "Modelo",
                    placeholder = "Verifone P630"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QaTextField(
                        value = state.operatingSystem,
                        onValueChange = { viewModel.onFieldChange(ProjectField.OS, it) },
                        label = "Sistema operativo",
                        modifier = Modifier.weight(1f)
                    )
                    QaTextField(
                        value = state.androidVersion,
                        onValueChange = { viewModel.onFieldChange(ProjectField.ANDROID_VERSION, it) },
                        label = "Versión Android",
                        placeholder = "11",
                        capitalization = KeyboardCapitalization.None,
                        modifier = Modifier.weight(1f)
                    )
                }
                QaTextField(
                    value = state.environment,
                    onValueChange = { viewModel.onFieldChange(ProjectField.ENVIRONMENT, it) },
                    label = "Ambiente",
                    placeholder = "QA / UAT / Producción"
                )
            }
        }

        SectionCard(title = "Ejecución") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QaTextField(
                    value = state.tester,
                    onValueChange = { viewModel.onFieldChange(ProjectField.TESTER, it) },
                    label = "Tester",
                    required = true,
                    error = state.errors[Validators.Fields.PROJECT_TESTER]
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QaTextField(
                        value = state.startDateText,
                        onValueChange = { viewModel.onFieldChange(ProjectField.START_DATE, it) },
                        label = "Fecha de inicio",
                        placeholder = "dd/MM/yyyy",
                        error = state.errors[Validators.Fields.PROJECT_DATES],
                        capitalization = KeyboardCapitalization.None,
                        modifier = Modifier.weight(1f)
                    )
                    QaTextField(
                        value = state.endDateText,
                        onValueChange = { viewModel.onFieldChange(ProjectField.END_DATE, it) },
                        label = "Fecha de fin",
                        placeholder = "dd/MM/yyyy",
                        capitalization = KeyboardCapitalization.None,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        SectionCard(title = "Detalle") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QaTextField(
                    value = state.description,
                    onValueChange = { viewModel.onFieldChange(ProjectField.DESCRIPTION, it) },
                    label = "Descripción",
                    singleLine = false,
                    minLines = 3
                )
                QaTextField(
                    value = state.notes,
                    onValueChange = { viewModel.onFieldChange(ProjectField.NOTES, it) },
                    label = "Notas",
                    singleLine = false,
                    minLines = 2
                )
            }
        }

        Text(
            text = "Los campos marcados con * son obligatorios.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
            ) {
                Text(if (state.isEditing) "Guardar cambios" else "Crear proyecto")
            }
        }
    }
}
