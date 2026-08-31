package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.core.Outcome
import com.qareport.generator.data.prefs.SettingsRepository
import com.qareport.generator.data.repository.BugRepository
import com.qareport.generator.data.repository.ExecutionRepository
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.data.repository.TestCaseRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.ExecutionTrendPoint
import com.qareport.generator.domain.model.ModuleStats
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.ProjectStats
import com.qareport.generator.domain.model.Report
import com.qareport.generator.domain.model.ReportFormat
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.report.ConclusionGenerator
import com.qareport.generator.domain.stats.StatsCalculator
import com.qareport.generator.report.ReportService
import com.qareport.generator.ui.common.BaseViewModel
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Todo lo que muestra el dashboard de un proyecto. */
data class ProjectDetailUiState(
    val project: Project? = null,
    val testCases: List<TestCase> = emptyList(),
    val bugs: List<Bug> = emptyList(),
    val stats: ProjectStats = ProjectStats(),
    val modules: List<ModuleStats> = emptyList(),
    val trend: List<ExecutionTrendPoint> = emptyList(),
    val conclusionPreview: String = ""
)

class ProjectDetailViewModel(
    private val projectId: Long,
    private val projectRepository: ProjectRepository,
    testCaseRepository: TestCaseRepository,
    bugRepository: BugRepository,
    executionRepository: ExecutionRepository,
    private val reportService: ReportService,
    private val settingsRepository: SettingsRepository
) : BaseViewModel() {

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** Último reporte generado, para ofrecer abrir/compartir sin volver al historial. */
    private val _lastReport = MutableStateFlow<Report?>(null)
    val lastReport: StateFlow<Report?> = _lastReport.asStateFlow()

    val uiState: StateFlow<ProjectDetailUiState> = combine(
        projectRepository.observeById(projectId),
        testCaseRepository.observeByProject(projectId),
        bugRepository.observeByProject(projectId),
        executionRepository.observeByProject(projectId)
    ) { project, cases, bugs, executions ->
        val stats = StatsCalculator.compute(cases, bugs)
        ProjectDetailUiState(
            project = project,
            testCases = cases,
            bugs = bugs,
            stats = stats,
            modules = StatsCalculator.byModule(cases),
            trend = StatsCalculator.executionTrend(executions),
            conclusionPreview = ConclusionGenerator.generate(stats).text
        )
    }.catch { error ->
        AppLogger.error("Fallo al cargar el detalle del proyecto", error)
        showError("No se pudo cargar el proyecto.")
        emit(ProjectDetailUiState())
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
        ProjectDetailUiState()
    )

    fun setFinalized(finalized: Boolean) {
        viewModelScope.launch {
            handle(
                projectRepository.setFinalized(projectId, finalized),
                successMessage = if (finalized) {
                    "Pruebas finalizadas. Ya puedes generar el reporte."
                } else {
                    "Proyecto reabierto para edición."
                }
            )
        }
    }

    fun generatePdf(onGenerated: (Report) -> Unit = {}) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            val settings = settingsRepository.currentSettings()
            when (val outcome = reportService.generatePdf(projectId, settings)) {
                is Outcome.Success -> {
                    _lastReport.value = outcome.data
                    showMessage("Reporte generado correctamente.")
                    onGenerated(outcome.data)
                }
                is Outcome.Failure -> showError(outcome.message)
            }
            _isGenerating.value = false
        }
    }

    fun export(format: ReportFormat, onExported: (Report) -> Unit = {}) {
        if (_isGenerating.value) return
        viewModelScope.launch {
            _isGenerating.value = true
            val settings = settingsRepository.currentSettings()
            val outcome = if (format == ReportFormat.PDF) {
                reportService.generatePdf(projectId, settings)
            } else {
                reportService.export(projectId, format, settings)
            }
            when (outcome) {
                is Outcome.Success -> {
                    _lastReport.value = outcome.data
                    showMessage("Archivo ${format.label} generado: ${outcome.data.fileName}")
                    onExported(outcome.data)
                }
                is Outcome.Failure -> showError(outcome.message)
            }
            _isGenerating.value = false
        }
    }

    fun clearLastReport() {
        _lastReport.value = null
    }

    companion object {
        fun factory(projectId: Long) = viewModelFactory {
            initializer {
                val container = appContainer()
                ProjectDetailViewModel(
                    projectId = projectId,
                    projectRepository = container.projectRepository,
                    testCaseRepository = container.testCaseRepository,
                    bugRepository = container.bugRepository,
                    executionRepository = container.executionRepository,
                    reportService = container.reportService,
                    settingsRepository = container.settingsRepository
                )
            }
        }
    }
}
