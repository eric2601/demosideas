package com.gastos.quincenal.ui.income

import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.domain.usecase.GetMonthOverviewUseCase
import com.gastos.quincenal.ui.common.MonthAwareViewModel
import com.gastos.quincenal.ui.common.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Lista de ingresos del mes agrupada por quincena. */
class IncomeListViewModel(
    getMonthOverview: GetMonthOverviewUseCase,
    private val budgetRepository: BudgetRepository
) : MonthAwareViewModel(getMonthOverview) {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun toggleReceived(incomeId: Long, date: LocalDate, received: Boolean) {
        viewModelScope.launch {
            budgetRepository.setIncomeReceived(incomeId, date, received)
        }
    }

    fun duplicate(incomeId: Long) {
        viewModelScope.launch {
            budgetRepository.duplicateIncome(incomeId)
            _message.value = UiMessage.of("Ingreso duplicado.")
        }
    }

    fun deleteSeries(incomeId: Long) {
        viewModelScope.launch {
            budgetRepository.deleteIncome(incomeId)
            _message.value = UiMessage.of("Ingreso eliminado.")
        }
    }

    fun deleteOccurrence(incomeId: Long, date: LocalDate) {
        viewModelScope.launch {
            budgetRepository.skipIncomeOccurrence(incomeId, date)
            _message.value = UiMessage.of("Se quito el ingreso de esta fecha.")
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
