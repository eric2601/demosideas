package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Project
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

class ProjectListViewModel(
    private val projectRepository: ProjectRepository
) : BaseViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Lista filtrada por el texto de búsqueda (nombre, cliente, producto o versión). */
    val projects: StateFlow<List<Project>> =
        combine(
            projectRepository.observeAll().catch { error ->
                AppLogger.error("Fallo al observar proyectos", error)
                showError("No se pudieron cargar los proyectos.")
                emit(emptyList())
            },
            _query
        ) { list, query ->
            val term = query.trim()
            if (term.isEmpty()) {
                list
            } else {
                list.filter { project ->
                    project.name.contains(term, ignoreCase = true) ||
                        project.client.contains(term, ignoreCase = true) ||
                        project.product.contains(term, ignoreCase = true) ||
                        project.version.contains(term, ignoreCase = true)
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
            emptyList()
        )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun delete(project: Project) {
        viewModelScope.launch {
            handle(
                projectRepository.delete(project.id),
                successMessage = "Proyecto \"${project.name}\" eliminado."
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ProjectListViewModel(appContainer().projectRepository) }
        }
    }
}
