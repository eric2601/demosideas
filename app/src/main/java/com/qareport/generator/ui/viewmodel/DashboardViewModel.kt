package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.core.Outcome
import com.qareport.generator.data.demo.DemoDataLoader
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.GlobalStats
import com.qareport.generator.domain.model.Project
import com.qareport.generator.ui.common.BaseViewModel
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    projectRepository: ProjectRepository,
    private val demoDataLoader: DemoDataLoader
) : BaseViewModel() {

    private val _isLoadingDemo = MutableStateFlow(false)
    val isLoadingDemo: StateFlow<Boolean> = _isLoadingDemo.asStateFlow()

    val globalStats: StateFlow<GlobalStats> = projectRepository.observeGlobalStats()
        .catch { error ->
            AppLogger.error("Fallo al observar estadísticas globales", error)
            showError("No se pudieron cargar las estadísticas.")
            emit(GlobalStats())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GlobalStats())

    val recentProjects: StateFlow<List<Project>> = projectRepository.observeRecent(RECENT_LIMIT)
        .catch { error ->
            AppLogger.error("Fallo al observar proyectos recientes", error)
            showError("No se pudieron cargar los proyectos recientes.")
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Carga el proyecto de demostración completo y notifica su id al terminar. */
    fun loadDemoData(onLoaded: (Long) -> Unit = {}) {
        if (_isLoadingDemo.value) return
        viewModelScope.launch {
            _isLoadingDemo.value = true
            when (val outcome = demoDataLoader.load()) {
                is Outcome.Success -> {
                    showMessage("Datos de demostración cargados.")
                    onLoaded(outcome.data)
                }
                is Outcome.Failure -> showError(outcome.message)
            }
            _isLoadingDemo.value = false
        }
    }

    companion object {
        private const val RECENT_LIMIT = 5
        internal const val STOP_TIMEOUT_MS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                DashboardViewModel(container.projectRepository, container.demoDataLoader)
            }
        }
    }
}
