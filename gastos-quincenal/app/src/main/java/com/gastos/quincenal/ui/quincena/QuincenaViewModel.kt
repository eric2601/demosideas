package com.gastos.quincenal.ui.quincena

import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.domain.usecase.GetMonthOverviewUseCase
import com.gastos.quincenal.ui.common.MonthAwareViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Detalle de una quincena concreta. */
class QuincenaViewModel(
    getMonthOverview: GetMonthOverviewUseCase,
    private val budgetRepository: BudgetRepository
) : MonthAwareViewModel(getMonthOverview) {

    private val _quincena = MutableStateFlow(Quincena.PRIMERA)
    val quincena: StateFlow<Quincena> = _quincena.asStateFlow()

    fun selectQuincena(quincena: Quincena) {
        _quincena.value = quincena
    }

    fun togglePaid(expenseId: Long, date: LocalDate, paid: Boolean) {
        viewModelScope.launch {
            budgetRepository.setExpensePaid(expenseId, date, paid)
        }
    }
}
