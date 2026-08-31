package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.core.Outcome
import com.qareport.generator.data.repository.TestCaseRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.validation.ValidationResult
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.ui.common.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TestCaseFormState(
    val id: Long = 0L,
    val code: String = "",
    val title: String = "",
    val module: String = "",
    val priority: Priority = Priority.MEDIUM,
    val preconditions: String = "",
    val steps: String = "",
    val expectedResult: String = "",
    val actualResult: String = "",
    val status: TestStatus = TestStatus.NOT_TESTED,
    val severity: Severity? = null,
    val comments: String = "",
    val orderIndex: Int = 0,
    val createdAt: Long = 0L,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errors: Map<String, String> = emptyMap()
) {
    val isEditing: Boolean get() = id > 0L
}

class TestCaseEditorViewModel(
    private val projectId: Long,
    private val testCaseId: Long,
    private val testCaseRepository: TestCaseRepository
) : BaseViewModel() {

    private val _state = MutableStateFlow(TestCaseFormState(isLoading = true))
    val state: StateFlow<TestCaseFormState> = _state.asStateFlow()

    /** Códigos ya usados en el proyecto, para validar duplicados. */
    private var existingCodes: List<String> = emptyList()

    init {
        viewModelScope.launch {
            existingCodes = testCaseRepository.getCodes(projectId)
            if (testCaseId > 0L) {
                loadExisting()
            } else {
                _state.value = TestCaseFormState(
                    code = testCaseRepository.suggestNextCode(projectId),
                    isLoading = false
                )
            }
        }
    }

    private suspend fun loadExisting() {
        val testCase = testCaseRepository.getById(testCaseId)
        if (testCase == null) {
            showError("El caso de prueba ya no está disponible.")
            _state.update { it.copy(isLoading = false) }
            return
        }
        // El propio código no cuenta como duplicado al editar.
        existingCodes = existingCodes.filterNot { it.equals(testCase.code, ignoreCase = true) }
        _state.value = TestCaseFormState(
            id = testCase.id,
            code = testCase.code,
            title = testCase.title,
            module = testCase.module,
            priority = testCase.priority,
            preconditions = testCase.preconditions,
            steps = testCase.steps,
            expectedResult = testCase.expectedResult,
            actualResult = testCase.actualResult,
            status = testCase.status,
            severity = testCase.severity,
            comments = testCase.comments,
            orderIndex = testCase.orderIndex,
            createdAt = testCase.createdAt,
            isLoading = false
        )
    }

    fun onCodeChange(value: String) = update { it.copy(code = value, errors = it.errors - Validators.Fields.TEST_CODE) }

    fun onTitleChange(value: String) = update { it.copy(title = value, errors = it.errors - Validators.Fields.TEST_TITLE) }

    fun onModuleChange(value: String) = update { it.copy(module = value) }

    fun onPriorityChange(value: Priority) = update { it.copy(priority = value) }

    fun onPreconditionsChange(value: String) = update { it.copy(preconditions = value) }

    fun onStepsChange(value: String) = update { it.copy(steps = value) }

    fun onExpectedChange(value: String) =
        update { it.copy(expectedResult = value, errors = it.errors - Validators.Fields.TEST_EXPECTED) }

    fun onActualChange(value: String) = update { it.copy(actualResult = value) }

    fun onStatusChange(value: TestStatus) = update { current ->
        // La severidad solo tiene sentido cuando el caso falla o queda bloqueado.
        val severity = if (value == TestStatus.FAIL || value == TestStatus.BLOCKED) current.severity else null
        current.copy(status = value, severity = severity)
    }

    fun onSeverityChange(value: Severity?) = update { it.copy(severity = value) }

    fun onCommentsChange(value: String) = update { it.copy(comments = value) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.isSaving) return

        val testCase = current.toTestCase()
        when (val validation = Validators.validateTestCase(testCase, existingCodes)) {
            is ValidationResult.Invalid -> {
                _state.update { it.copy(errors = validation.errors.associate { e -> e.field to e.message }) }
                showError(validation.firstMessage ?: "Revisa los campos obligatorios.")
                return
            }
            ValidationResult.Valid -> Unit
        }

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errors = emptyMap()) }
            val outcome = if (current.isEditing) {
                testCaseRepository.update(testCase)
            } else {
                testCaseRepository.create(testCase).let { result ->
                    when (result) {
                        is Outcome.Success -> Outcome.Success(Unit)
                        is Outcome.Failure -> result
                    }
                }
            }
            _state.update { it.copy(isSaving = false) }
            if (handle(outcome, if (current.isEditing) "Caso actualizado." else "Caso creado.")) {
                onSaved()
            }
        }
    }

    private fun update(block: (TestCaseFormState) -> TestCaseFormState) {
        _state.update(block)
    }

    private fun TestCaseFormState.toTestCase(): TestCase = TestCase(
        id = id,
        projectId = projectId,
        code = code.trim().uppercase(),
        title = title.trim(),
        module = module.trim(),
        priority = priority,
        preconditions = preconditions.trim(),
        steps = steps.trim(),
        expectedResult = expectedResult.trim(),
        actualResult = actualResult.trim(),
        status = status,
        severity = severity,
        comments = comments.trim(),
        orderIndex = orderIndex,
        createdAt = createdAt,
        updatedAt = 0L
    )

    companion object {
        fun factory(projectId: Long, testCaseId: Long) = viewModelFactory {
            initializer {
                TestCaseEditorViewModel(projectId, testCaseId, appContainer().testCaseRepository)
            }
        }
    }
}
