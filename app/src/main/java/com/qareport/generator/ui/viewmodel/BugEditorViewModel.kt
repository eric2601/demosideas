package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.core.Outcome
import com.qareport.generator.data.repository.BugRepository
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.validation.ValidationResult
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.ui.common.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BugFormState(
    val id: Long = 0L,
    val code: String = "",
    val title: String = "",
    val description: String = "",
    val severity: Severity = Severity.MAJOR,
    val priority: Priority = Priority.HIGH,
    val stepsToReproduce: String = "",
    val expectedResult: String = "",
    val actualResult: String = "",
    val version: String = "",
    val device: String = "",
    val status: BugStatus = BugStatus.OPEN,
    val testCaseId: Long? = null,
    val createdAt: Long = 0L,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errors: Map<String, String> = emptyMap()
) {
    val isEditing: Boolean get() = id > 0L
}

class BugEditorViewModel(
    private val projectId: Long,
    private val bugId: Long,
    private val bugRepository: BugRepository,
    private val projectRepository: ProjectRepository
) : BaseViewModel() {

    private val _state = MutableStateFlow(BugFormState())
    val state: StateFlow<BugFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (bugId > 0L) {
                val bug = bugRepository.getById(bugId)
                if (bug == null) {
                    showError("El defecto ya no está disponible.")
                    _state.update { it.copy(isLoading = false) }
                    return@launch
                }
                _state.value = BugFormState(
                    id = bug.id,
                    code = bug.code,
                    title = bug.title,
                    description = bug.description,
                    severity = bug.severity,
                    priority = bug.priority,
                    stepsToReproduce = bug.stepsToReproduce,
                    expectedResult = bug.expectedResult,
                    actualResult = bug.actualResult,
                    version = bug.version,
                    device = bug.device,
                    status = bug.status,
                    testCaseId = bug.testCaseId,
                    createdAt = bug.createdAt,
                    isLoading = false
                )
            } else {
                // Versión y dispositivo se heredan del proyecto: son datos reales, no inventados.
                val project = projectRepository.getById(projectId)
                _state.value = BugFormState(
                    code = bugRepository.suggestNextCode(projectId),
                    version = project?.version.orEmpty(),
                    device = project?.let { it.deviceModel.ifBlank { it.deviceType } }.orEmpty(),
                    isLoading = false
                )
            }
        }
    }

    fun onTitleChange(value: String) =
        _state.update { it.copy(title = value, errors = it.errors - Validators.Fields.BUG_TITLE) }

    fun onDescriptionChange(value: String) =
        _state.update { it.copy(description = value, errors = it.errors - Validators.Fields.BUG_DESCRIPTION) }

    fun onStepsChange(value: String) =
        _state.update { it.copy(stepsToReproduce = value, errors = it.errors - Validators.Fields.BUG_STEPS) }

    fun onExpectedChange(value: String) = _state.update { it.copy(expectedResult = value) }

    fun onActualChange(value: String) = _state.update { it.copy(actualResult = value) }

    fun onVersionChange(value: String) = _state.update { it.copy(version = value) }

    fun onDeviceChange(value: String) = _state.update { it.copy(device = value) }

    fun onSeverityChange(value: Severity) = _state.update { it.copy(severity = value) }

    fun onPriorityChange(value: Priority) = _state.update { it.copy(priority = value) }

    fun onStatusChange(value: BugStatus) = _state.update { it.copy(status = value) }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        if (current.isSaving) return

        val validation = Validators.validateBug(
            title = current.title,
            description = current.description,
            stepsToReproduce = current.stepsToReproduce
        )
        if (validation is ValidationResult.Invalid) {
            _state.update { it.copy(errors = validation.errors.associate { e -> e.field to e.message }) }
            showError(validation.firstMessage ?: "Revisa los campos obligatorios.")
            return
        }

        val bug = Bug(
            id = current.id,
            projectId = projectId,
            testCaseId = current.testCaseId,
            code = current.code.trim().uppercase(),
            title = current.title.trim(),
            description = current.description.trim(),
            severity = current.severity,
            priority = current.priority,
            stepsToReproduce = current.stepsToReproduce.trim(),
            expectedResult = current.expectedResult.trim(),
            actualResult = current.actualResult.trim(),
            version = current.version.trim(),
            device = current.device.trim(),
            status = current.status,
            createdAt = current.createdAt,
            updatedAt = 0L
        )

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errors = emptyMap()) }
            val outcome = if (current.isEditing) {
                bugRepository.update(bug)
            } else {
                when (val created = bugRepository.create(bug)) {
                    is Outcome.Success -> Outcome.Success(Unit)
                    is Outcome.Failure -> created
                }
            }
            _state.update { it.copy(isSaving = false) }
            if (handle(outcome, if (current.isEditing) "Defecto actualizado." else "Defecto registrado.")) {
                onSaved()
            }
        }
    }

    companion object {
        fun factory(projectId: Long, bugId: Long) = viewModelFactory {
            initializer {
                val container = appContainer()
                BugEditorViewModel(projectId, bugId, container.bugRepository, container.projectRepository)
            }
        }
    }
}
