package com.qareport.generator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.ReportFormat
import com.qareport.generator.domain.model.ThemeMode
import com.qareport.generator.ui.components.SectionCard
import com.qareport.generator.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    appVersion: String,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard(title = "Tema") {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                            Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Formato de reporte") {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = true, onClick = {})
                        Text(ReportFormat.PDF.label, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "El reporte principal se genera en PDF. Desde el detalle del proyecto " +
                            "también puedes exportar CSV, JSON y TXT.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            SectionCard(title = "Contenido del reporte") {
                Column {
                    SettingSwitch(
                        title = "Mostrar logs",
                        description = "Incluye los logs guardados en el PDF.",
                        checked = settings.includeLogs,
                        onCheckedChange = viewModel::setIncludeLogs
                    )
                    SettingSwitch(
                        title = "Incluir evidencias",
                        description = "Adjunta imágenes y referencias de archivos.",
                        checked = settings.includeEvidence,
                        onCheckedChange = viewModel::setIncludeEvidence
                    )
                    SettingSwitch(
                        title = "Incluir casos PASS",
                        description = "Muestra en la matriz los casos aprobados.",
                        checked = settings.includePassedCases,
                        onCheckedChange = viewModel::setIncludePassedCases
                    )
                    SettingSwitch(
                        title = "Incluir casos FAIL",
                        description = "Muestra en la matriz los casos fallidos.",
                        checked = settings.includeFailedCases,
                        onCheckedChange = viewModel::setIncludeFailedCases
                    )
                    SettingSwitch(
                        title = "Incluir conclusión automática",
                        description = "Se calcula únicamente con los resultados registrados.",
                        checked = settings.includeAutoConclusion,
                        onCheckedChange = viewModel::setIncludeAutoConclusion
                    )
                    SettingSwitch(
                        title = "Registro técnico detallado",
                        description = "Aumenta el detalle de los mensajes en Logcat.",
                        checked = settings.showTechnicalLogs,
                        onCheckedChange = viewModel::setShowTechnicalLogs
                    )
                }
            }
        }

        item {
            SectionCard(title = "Privacidad") {
                Text(
                    text = "La aplicación funciona completamente offline. No se declara el permiso " +
                        "de Internet, por lo que la información de tus proyectos nunca sale del " +
                        "dispositivo salvo que compartas un archivo explícitamente.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item {
            SectionCard(title = "Acerca de") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("QA Report Generator $appVersion", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = viewModel::resetToDefaults,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Restaurar configuración por defecto") }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
