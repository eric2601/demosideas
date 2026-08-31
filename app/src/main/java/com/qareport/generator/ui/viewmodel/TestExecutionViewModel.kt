package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.core.Outcome
import com.qareport.generator.data.repository.BugRepository
import com.qareport.generator.data.repository.ExecutionRepository
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.data.repository.TestCaseRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.ProjectStats
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestExecution
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.stats.StatsCalculator
import com.qareport.generator.domain.validation.ValidationResult
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.ui.common.BaseViewModel
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Datos obligatorios que se piden al marcar un caso como FAIL. */
data class FailFormState(
    val problemDescription: String = "",
    val severity: Severity? = null,
    val stepsToReproduce: String = "",
    val expectedResult: String = "",
    val actualResult: String = "",
    val createBug: Boolean = true,
    val errors: Map<String, String> = emptyMap()
)

data class ExecutionUiState(
    val cases: List<TestCase> = emptyList(),
    val currentIndex: Int = 0,
    val stats: ProjectStats = ProjectStats(),
    val isSaving: Boolean = false
) {
    val currentCase: TestCase? get() = cases.getOrNull(currentIndex)
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() = currentIndex < cases.lastIndex
    val position: Int get() = if (cases.isEmpty()) 0 else currentIndex + 1
}

/**
 * Ejecución secuencial de casos de prueba.
 *
 * Al registrar un FAIL se exige la información de diagnóstico completa y, si el usuario
 * lo indica, se crea automáticamente el defecto asociado con esos mismos datos (sin
 * inventar ninguno).
 */
class TestExecutionViewModel(
    private val projectId: Long,
    private val testCaseRepository: TestCaseRepository,
    private val executionRepository: ExecutionRepository,
    private val bugRepository: BugRepository,
    private val projectRepository: ProjectRepository
) : BaseViewModel() {

    private val _currentIndex = MutableStateFlow(0)
    private val _isSaving = MutableStateFlow(false)

    private val _failForm = MutableStateFlow(FailFormState())
    val failForm: StateFlow<FailFormState> = _failForm.asStateFlow()

    /** Notas libres que se guardan como comentarios del caso. */
    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes.asStateFlow()

    private var testerName: String = ""
    private var projectVersion: String = ""
    private var projectDevice: String = ""

    val uiState: StateFlow<ExecutionUiState> = combine(
        testCaseRepository.observeByProject(projectId).catch { error ->
            AppLogger.error("Fallo al cargar los casos para ejecución", error)
            showError("No se pudieron cargar los casos de prueba.")
            emit(emptyList())
        },
        _currentIndex,
        _isSaving
    ) { cases, index, isSaving ->
        ExecutionUiState(
            cases = cases,
            currentIndex = index.coerceIn(0, maxOf(0, cases.lastIndex)),
            stats = StatsCalculator.compute(cases),
            isSaving = isSaving
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
        ExecutionUiState()
    )

    init {
        viewModelScope.launch {
            projectRepository.getById(projectId)?.let { project ->
                testerName = project.tester
                projectVersion = project.version
                projectDevice = project.deviceModel.ifBlank { project.deviceType }
            }
            // Empieza en el primer caso pendiente para no repetir trabajo ya hecho.
            val cases = testCaseRepository.getByProject(projectId)
            val firstPending = cases.indexOfFirst { it.status == TestStatus.NOT_TESTED }
            if (firstPending >= 0) _currentIndex.value = firstPending
        }
    }

    fun goToPrevious() {
        _currentIndex.update { (it - 1).coerceAtLeast(0) }
        resetForms()
    }

    fun goToNext() {
        val last = uiState.value.cases.lastIndex
        _currentIndex.update { (it + 1).coerceAtMost(maxOf(0, last)) }
        resetForms()
    }

    fun goTo(index: Int) {
        val last = uiState.value.cases.lastIndex
        _currentIndex.value = index.coerceIn(0, maxOf(0, last))
        resetForms()
    }

    fun onNotesChange(value: String) {
        _notes.value = value
    }

    fun onFailFieldChange(transform: FailFormState.() -> FailFormState) {
        _failForm.update { current -> current.transform() }
    }

    fun resetFailForm() {
        _failForm.value = FailFormState()
    }

    /** Registra PASS, BLOCKED o SKIPPED, que no requieren información adicional. */
    fun recordSimpleResult(status: TestStatus, onRecorded: () -> Unit = {}) {
        if (status == TestStatus.FAIL) {
            showError("Para registrar un FAIL debes completar el formulario de diagnóstico.")
            return
        }
        val case = uiState.value.currentCase ?: return

        viewModelScope.launch {
            _isSaving.value = true
            val execution = TestExecution(
                projectId = projectId,
                testCaseId = case.id,
                status = status,
                executedAt = System.currentTimeMillis(),
                executedBy = testerName,
                notes = _notes.value.trim(),
                actualResult = case.actualResult
            )
            val recorded = executionRepository.record(execution, comments = _notes.value.trim())
            _isSaving.value = false
            if (handle(recorded, "Resultado ${status.label} registrado en ${case.code}.")) {
                resetForms()
                advanceIfPossible()
                onRecorded()
            }
        }
    }

    /** Registra un FAIL tras validar toda la información obligatoria. */
    fun recordFail(onRecorded: () -> Unit = {}) {
        val case = uiState.value.currentCase ?: return
        val form = _failForm.value

        val validation = Validators.validateFailDetails(
            status = TestStatus.FAIL,
            problemDescription = form.problemDescription,
            severity = form.severity,
            stepsToReproduce = form.stepsToReproduce,
            expectedResult = form.expectedResult,
            actualResult = form.actualResult
        )
        if (validation is ValidationResult.Invalid) {
            _failForm.update { it.copy(errors = validation.errors.associate { e -> e.field to e.message }) }
            showError(validation.firstMessage ?: "Completa la información del fallo.")
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            val now = System.currentTimeMillis()
            val execution = TestExecution(
                projectId = projectId,
                testCaseId = case.id,
                status = TestStatus.FAIL,
                executedAt = now,
                executedBy = testerName,
                notes = _notes.value.trim(),
                problemDescription = form.problemDescription.trim(),
                severity = form.severity,
                stepsToReproduce = form.stepsToReproduce.trim(),
                expectedResult = form.expectedResult.trim(),
                actualResult = form.actualResult.trim()
            )
            val recorded = executionRepository.record(execution, comments = _notes.value.trim())

            if (recorded is Outcome.Success && form.createBug) {
                createBugFromFailure(case, form)
            }
            _isSaving.value = false

            if (handle(recorded, "FAIL registrado en ${case.code}.")) {
                resetForms()
                advanceIfPossible()
                onRecorded()
            }
        }
    }

    private suspend fun createBugFromFailure(case: TestCase, form: FailFormState) {
        val bug = Bug(
            projectId = projectId,
            testCaseId = case.id,
            code = bugRepository.suggestNextCode(projectId),
            title = form.problemDescription.trim().lineSequence().first().take(120),
            description = form.problemDescription.trim(),
            severity = form.severity ?: Severity.MAJOR,
            priority = when (form.severity) {
                Severity.BLOCKER, Severity.CRITICAL -> Priority.CRITICAL
                Severity.MAJOR -> Priority.HIGH
                else -> Priority.MEDIUM
            },
            stepsToReproduce = form.stepsToReproduce.trim(),
            expectedResult = form.expectedResult.trim(),
            actualResult = form.actualResult.trim(),
            version = projectVersion,
            device = projectDevice
        )
        when (val outcome = bugRepository.create(bug)) {
            is Outcome.Success -> showMessage("Defecto ${bug.code} creado a partir del fallo.")
            is Outcome.Failure -> showError(outcome.message)
        }
    }

    private fun advanceIfPossible() {
        val state = uiState.value
        if (state.hasNext) _currentIndex.value = state.currentIndex + 1
    }

    private fun resetForms() {
        _failForm.value = FailFormState()
        _notes.value = ""
    }

    companion object {
        fun factory(projectId: Long) = viewModelFactory {
            initializer {
                val container = appContainer()
                TestExecutionViewModel(
                    projectId = projectId,
                    testCaseRepository = container.testCaseRepository,
                    executionRepository = container.executionRepository,
                    bugRepository = container.bugRepository,
                    projectRepository = container.projectRepository
                )
            }
        }
    }
}
