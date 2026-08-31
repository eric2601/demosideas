package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.data.repository.BugRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.ui.common.BaseViewModel
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BugViewModel(
    private val projectId: Long,
    private val bugRepository: BugRepository
) : BaseViewModel() {

    private val _statusFilter = MutableStateFlow<BugStatus?>(null)
    val statusFilter: StateFlow<BugStatus?> = _statusFilter.asStateFlow()

    private val allBugs: StateFlow<List<Bug>> = bugRepository.observeByProject(projectId)
        .catch { error ->
            AppLogger.error("Fallo al observar defectos", error)
            showError("No se pudieron cargar los defectos.")
            emit(emptyList())
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
            emptyList()
        )

    val bugs: StateFlow<List<Bug>> = combine(allBugs, _statusFilter) { list, status ->
        if (status == null) list else list.filter { it.status == status }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
        emptyList()
    )

    val severityCounts: StateFlow<Map<Severity, Int>> = allBugs
        .map { list -> Severity.entries.associateWith { severity -> list.count { it.severity == severity } } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
            emptyMap()
        )

    fun onStatusFilterChange(status: BugStatus?) {
        _statusFilter.value = if (_statusFilter.value == status) null else status
    }

    fun updateStatus(bug: Bug, status: BugStatus) {
        viewModelScope.launch {
            handle(
                bugRepository.updateStatus(bug.id, status),
                successMessage = "${bug.code} marcado como ${status.label}."
            )
        }
    }

    fun delete(bug: Bug) {
        viewModelScope.launch {
            handle(bugRepository.delete(bug.id), successMessage = "${bug.code} eliminado.")
        }
    }

    companion object {
        fun factory(projectId: Long) = viewModelFactory {
            initializer { BugViewModel(projectId, appContainer().bugRepository) }
        }
    }
}
