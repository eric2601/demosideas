package com.gastos.quincenal.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.data.repository.SettingsRepository
import com.gastos.quincenal.domain.model.MonthHistoryEntry
import com.gastos.quincenal.domain.usecase.GetHistoryUseCase
import com.gastos.quincenal.utils.CurrencyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Estado del historial anual. */
data class HistoryUiState(
    val year: Int = LocalDate.now().year,
    val entries: List<MonthHistoryEntry> = emptyList(),
    val isLoading: Boolean = true
)

class HistoryViewModel(
    private val getHistory: GetHistoryUseCase,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    val formatter: StateFlow<CurrencyFormatter> = settingsRepository.observeSettings()
        .map { it.formatter() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), CurrencyFormatter.MXN)

    init {
        loadYear(_state.value.year)
    }

    fun loadYear(year: Int) {
        _state.value = _state.value.copy(year = year, isLoading = true)
        viewModelScope.launch {
            val entries = getHistory(year)
            _state.value = HistoryUiState(year = year, entries = entries, isLoading = false)
        }
    }

    fun previousYear() = loadYear(_state.value.year - 1)

    fun nextYear() = loadYear(_state.value.year + 1)
}
