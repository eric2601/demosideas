package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.core.Outcome
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.validation.ValidationResult
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.ui.common.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Estado del formulario de proyecto. Las fechas se editan como texto `dd/MM/yyyy`
 * y solo se convierten a epoch al guardar, para poder mostrar errores de formato.
 */
data class ProjectFormState(
    val id: Long = 0L,
    val name: String = "",
    val client: String = "",
    val product: String = "",
    val version: String = "",
    val buildNumber: String = "",
    val deviceType: String = "",
    val deviceModel: String = "",
    val operatingSystem: String = "Android",
    val androidVersion: String = "",
    val tester: String = "",
    val environment: String = "QA",
    val startDateText: String = "",
    val endDateText: String = "",
    val description: String = "",
    val notes: String = "",
    val finalized: Boolean = false,
    val createdAt: Long = 0L,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errors: Map<String, String> = emptyMap()
) {
    val isEditing: Boolean get() = id > 0L
}

class ProjectEditorViewModel(
    private val projectRepository: ProjectRepository,
    private val projectId: Long
) : BaseViewModel() {

    private val _state = MutableStateFlow(ProjectFormState(isLoading = projectId > 0L))
    val state: StateFlow<ProjectFormState> = _state.asStateFlow()

    init {
        if (projectId > 0L) loadProject()
    }

    private fun loadProject() {
        viewModelScope.launch {
            val project = projectRepository.getById(projectId)
            if (project == null) {
                showError("El proyecto ya no está disponible.")
                _state.update { it.copy(isLoading = false) }
                return@launch
            }
            _state.value = ProjectFormState(
                id = project.id,
                name = project.name,
                client = project.client,
                product = project.product,
                version = project.version,
                buildNumber = project.buildNumber,
                deviceType = project.deviceType,
                deviceModel = project.deviceModel,
                operatingSystem = project.operatingSystem,
                androidVersion = project.androidVersion,
                tester = project.tester,
                environment = project.environment,
                startDateText = project.startDate?.takeIf { it > 0L }
                    ?.let { DateFormats.formatDate(it) }.orEmpty(),
                endDateText = project.endDate?.takeIf { it > 0L }
                    ?.let { DateFormats.formatDate(it) }.orEmpty(),
                description = project.description,
                notes = project.notes,
                finalized = project.finalized,
                createdAt = project.createdAt,
                isLoading = false
            )
        }
    }

    fun onFieldChange(field: ProjectField, value: String) {
        _state.update { current ->
            val updated = when (field) {
                ProjectField.NAME -> current.copy(name = value)
                ProjectField.CLIENT -> current.copy(client = value)
                ProjectField.PRODUCT -> current.copy(product = value)
                ProjectField.VERSION -> current.copy(version = value)
                ProjectField.BUILD -> current.copy(buildNumber = value)
                ProjectField.DEVICE_TYPE -> current.copy(deviceType = value)
                ProjectField.DEVICE_MODEL -> current.copy(deviceModel = value)
                ProjectField.OS -> current.copy(operatingSystem = value)
                ProjectField.ANDROID_VERSION -> current.copy(androidVersion = value)
                ProjectField.TESTER -> current.copy(tester = value)
                ProjectField.ENVIRONMENT -> current.copy(environment = value)
                ProjectField.START_DATE -> current.copy(startDateText = value)
                ProjectField.END_DATE -> current.copy(endDateText = value)
                ProjectField.DESCRIPTION -> current.copy(description = value)
                ProjectField.NOTES -> current.copy(notes = value)
            }
            // Al escribir se limpia el error del campo tocado.
            updated.copy(errors = updated.errors - field.key)
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val current = _state.value
        if (current.isSaving) return

        if (current.startDateText.isNotBlank() && !Validators.isValidDateInput(current.startDateText)) {
            setError(Validators.Fields.PROJECT_DATES, "La fecha de inicio debe tener el formato dd/MM/yyyy.")
            return
        }
        if (current.endDateText.isNotBlank() && !Validators.isValidDateInput(current.endDateText)) {
            setError(Validators.Fields.PROJECT_DATES, "La fecha de finalización debe tener el formato dd/MM/yyyy.")
            return
        }

        val project = current.toProject()
        when (val validation = Validators.validateProject(project)) {
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
                projectRepository.update(project).let { result ->
                    when (result) {
                        is Outcome.Success -> Outcome.Success(project.id)
                        is Outcome.Failure -> result
                    }
                }
            } else {
                projectRepository.create(project)
            }
            _state.update { it.copy(isSaving = false) }

            when (outcome) {
                is Outcome.Success -> {
                    showMessage(if (current.isEditing) "Proyecto actualizado." else "Proyecto creado.")
                    onSaved(outcome.data)
                }
                is Outcome.Failure -> showError(outcome.message)
            }
        }
    }

    private fun setError(field: String, message: String) {
        _state.update { it.copy(errors = it.errors + (field to message)) }
        showError(message)
    }

    private fun ProjectFormState.toProject(): Project = Project(
        id = id,
        name = name.trim(),
        client = client.trim(),
        product = product.trim(),
        version = version.trim(),
        buildNumber = buildNumber.trim(),
        deviceType = deviceType.trim(),
        deviceModel = deviceModel.trim(),
        operatingSystem = operatingSystem.trim(),
        androidVersion = androidVersion.trim(),
        tester = tester.trim(),
        environment = environment.trim(),
        startDate = DateFormats.parseDateOrNull(startDateText),
        endDate = DateFormats.parseDateOrNull(endDateText),
        description = description.trim(),
        notes = notes.trim(),
        finalized = finalized,
        createdAt = createdAt,
        updatedAt = 0L
    )

    companion object {
        fun factory(projectId: Long) = viewModelFactory {
            initializer { ProjectEditorViewModel(appContainer().projectRepository, projectId) }
        }
    }
}

/** Campos editables del formulario de proyecto. */
enum class ProjectField(val key: String) {
    NAME(Validators.Fields.PROJECT_NAME),
    CLIENT("client"),
    PRODUCT("product"),
    VERSION(Validators.Fields.PROJECT_VERSION),
    BUILD("build"),
    DEVICE_TYPE("device_type"),
    DEVICE_MODEL("device_model"),
    OS("os"),
    ANDROID_VERSION("android_version"),
    TESTER(Validators.Fields.PROJECT_TESTER),
    ENVIRONMENT("environment"),
    START_DATE(Validators.Fields.PROJECT_DATES),
    END_DATE("end_date"),
    DESCRIPTION("description"),
    NOTES("notes")
}
