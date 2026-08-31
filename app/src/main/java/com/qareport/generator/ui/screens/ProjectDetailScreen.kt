package com.qareport.generator.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qareport.generator.domain.model.Report
import com.qareport.generator.domain.model.ReportFormat
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.util.TextUtils
import com.qareport.generator.ui.components.ChartSlice
import com.qareport.generator.ui.components.DonutChart
import com.qareport.generator.ui.components.HorizontalBarChart
import com.qareport.generator.ui.components.LabeledValue
import com.qareport.generator.ui.components.LineChart
import com.qareport.generator.ui.components.LoadingOverlay
import com.qareport.generator.ui.components.ProgressBarQa
import com.qareport.generator.ui.components.SectionCard
import com.qareport.generator.ui.components.TrendPoint
import com.qareport.generator.ui.theme.QaColors
import com.qareport.generator.ui.viewmodel.ProjectDetailViewModel

/**
 * Dashboard de un proyecto: estadísticas, gráficas, accesos a los módulos y
 * generación del reporte.
 */
@Composable
fun ProjectDetailScreen(
    viewModel: ProjectDetailViewModel,
    onOpenTestCases: () -> Unit,
    onOpenExecution: () -> Unit,
    onOpenBugs: () -> Unit,
    onOpenEvidence: () -> Unit,
    onOpenLogs: () -> Unit,
    onReportGenerated: (Report) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val statusColors = QaColors.status
    val stats = state.stats

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(title = "Resumen QA") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ProgressBarQa(executed = stats.executed, total = stats.total)
                        DonutChart(
                            slices = listOf(
                                ChartSlice("PASS", stats.passed, statusColors.pass),
                                ChartSlice("FAIL", stats.failed, statusColors.fail),
                                ChartSlice("BLOCKED", stats.blocked, statusColors.blocked),
                                ChartSlice("SKIPPED", stats.skipped, statusColors.skipped),
                                ChartSlice("NOT TESTED", stats.notTested, statusColors.pending)
                            ),
                            centerValue = TextUtils.formatPercent(stats.passRate),
                            centerLabel = "PASS RATE"
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Defectos por severidad") {
                    if (stats.bugsTotal == 0) {
                        Text(
                            "No hay defectos registrados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        HorizontalBarChart(
                            slices = Severity.entries.map { severity ->
                                ChartSlice(
                                    label = severity.label,
                                    value = stats.countOfSeverity(severity),
                                    color = statusColors.colorFor(severity)
                                )
                            }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Pruebas por módulo") {
                    if (state.modules.isEmpty()) {
                        Text(
                            "Aún no hay casos de prueba registrados.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        HorizontalBarChart(
                            slices = state.modules.take(MAX_MODULES_IN_CHART).map { module ->
                                ChartSlice(
                                    label = module.module,
                                    value = module.total,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Evolución de la ejecución") {
                    if (state.trend.isEmpty()) {
                        Text(
                            "Todavía no se han registrado ejecuciones.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LineChart(
                            points = state.trend.map { TrendPoint(it.label, it.executed) }
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Módulos del proyecto") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModuleButton("Casos de prueba (${stats.total})", Icons.Filled.PlaylistAddCheck, onOpenTestCases)
                        ModuleButton("Ejecutar pruebas", Icons.Filled.PlayArrow, onOpenExecution)
                        ModuleButton("Defectos (${stats.bugsTotal})", Icons.Filled.BugReport, onOpenBugs)
                        ModuleButton("Evidencias", Icons.Filled.Image, onOpenEvidence)
                        ModuleButton("Logs", Icons.Filled.Terminal, onOpenLogs)
                    }
                }
            }

            item {
                SectionCard(title = "Información del proyecto") {
                    val project = state.project
                    if (project == null) {
                        Text("Cargando…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Column {
                            LabeledValue("Cliente", project.client)
                            LabeledValue("Producto", project.product)
                            LabeledValue("Versión", project.version)
                            LabeledValue("Build", project.buildNumber)
                            LabeledValue("Dispositivo", project.deviceModel)
                            LabeledValue("Android", project.androidVersion)
                            LabeledValue("Ambiente", project.environment)
                            LabeledValue("Tester", project.tester)
                            LabeledValue("Descripción", project.description)

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Pruebas finalizadas", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Actívalo cuando termines la ejecución para poder generar el reporte.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = project.finalized,
                                    onCheckedChange = viewModel::setFinalized
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionCard(title = "Conclusión automática (vista previa)") {
                    Text(
                        text = state.conclusionPreview.ifBlank {
                            "Se generará automáticamente a partir de los resultados registrados."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                SectionCard(title = "Reporte y exportación") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.generatePdf(onReportGenerated) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isGenerating
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null)
                            Text("  GENERAR REPORTE QA (PDF)")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.export(ReportFormat.CSV, onReportGenerated) },
                                modifier = Modifier.weight(1f),
                                enabled = !isGenerating
                            ) { Text("CSV") }
                            OutlinedButton(
                                onClick = { viewModel.export(ReportFormat.JSON, onReportGenerated) },
                                modifier = Modifier.weight(1f),
                                enabled = !isGenerating
                            ) { Text("JSON") }
                            OutlinedButton(
                                onClick = { viewModel.export(ReportFormat.TXT, onReportGenerated) },
                                modifier = Modifier.weight(1f),
                                enabled = !isGenerating
                            ) { Text("TXT") }
                        }
                        Text(
                            text = if (stats.notTested > 0 && state.project?.finalized != true) {
                                "Quedan ${stats.notTested} casos sin ejecutar: finaliza las pruebas " +
                                    "para poder generar el PDF."
                            } else {
                                "El PDF incluirá portada, resumen, matriz de pruebas, fallos, " +
                                    "defectos, evidencias y conclusión."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = "Pendientes: ${stats.pending} · Ejecutados: ${stats.executed} de ${stats.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Casos ${TestStatus.PASS.label}: ${stats.passed} · " +
                            "${TestStatus.FAIL.label}: ${stats.failed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isGenerating) {
            LoadingOverlay(
                message = "Generando archivo…",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ModuleButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp)
    ) {
        Icon(icon, contentDescription = null)
        Text("   $label", modifier = Modifier.weight(1f))
    }
}

private const val MAX_MODULES_IN_CHART = 8
