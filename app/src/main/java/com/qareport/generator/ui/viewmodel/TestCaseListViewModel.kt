package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.data.repository.TestCaseRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus
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

class TestCaseListViewModel(
    private val projectId: Long,
    private val testCaseRepository: TestCaseRepository
) : BaseViewModel() {

    private val _statusFilter = MutableStateFlow<TestStatus?>(null)
    val statusFilter: StateFlow<TestStatus?> = _statusFilter.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val allCases: StateFlow<List<TestCase>> =
        testCaseRepository.observeByProject(projectId)
            .catch { error ->
                AppLogger.error("Fallo al observar casos de prueba", error)
                showError("No se pudieron cargar los casos de prueba.")
                emit(emptyList())
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
                emptyList()
            )

    val testCases: StateFlow<List<TestCase>> =
        combine(allCases, _statusFilter, _query) { cases, status, query ->
            val term = query.trim()
            cases.filter { case ->
                (status == null || case.status == status) &&
                    (
                        term.isEmpty() ||
                            case.code.contains(term, ignoreCase = true) ||
                            case.title.contains(term, ignoreCase = true) ||
                            case.module.contains(term, ignoreCase = true)
                        )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
            emptyList()
        )

    /** Conteo por estado sobre la lista completa, no sobre la filtrada. */
    val counts: StateFlow<Map<TestStatus, Int>> = allCases
        .map { cases -> TestStatus.entries.associateWith { status -> cases.count { it.status == status } } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
            emptyMap()
        )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onStatusFilterChange(status: TestStatus?) {
        _statusFilter.value = if (_statusFilter.value == status) null else status
    }

    fun delete(testCase: TestCase) {
        viewModelScope.launch {
            handle(
                testCaseRepository.delete(testCase.id),
                successMessage = "Caso ${testCase.code} eliminado."
            )
        }
    }

    fun resetResults() {
        viewModelScope.launch {
            handle(
                testCaseRepository.resetResults(projectId),
                successMessage = "Resultados reiniciados."
            )
        }
    }

    companion object {
        fun factory(projectId: Long) = viewModelFactory {
            initializer { TestCaseListViewModel(projectId, appContainer().testCaseRepository) }
        }
    }
}
