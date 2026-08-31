package com.gastos.quincenal.ui.dashboard

import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.domain.usecase.DuplicateMonthUseCase
import com.gastos.quincenal.domain.usecase.DuplicateOptions
import com.gastos.quincenal.domain.usecase.GetMonthOverviewUseCase
import com.gastos.quincenal.ui.common.MonthAwareViewModel
import com.gastos.quincenal.ui.common.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Estado del tablero mensual. */
class DashboardViewModel(
    getMonthOverview: GetMonthOverviewUseCase,
    private val budgetRepository: BudgetRepository,
    private val duplicateMonth: DuplicateMonthUseCase
) : MonthAwareViewModel(getMonthOverview) {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun togglePaid(expenseId: Long, date: LocalDate, paid: Boolean) {
        viewModelScope.launch {
            budgetRepository.setExpensePaid(expenseId, date, paid)
        }
    }

    /**
     * Copia al mes siguiente respetando las reglas de recurrencia: los gastos de
     * un solo mes no se arrastran.
     */
    fun copyToNextMonth() {
        val source = yearMonth.value
        viewModelScope.launch {
            val result = duplicateMonth(source, source.plusMonths(1), DuplicateOptions.ONLY_RECURRING)
            _message.value = UiMessage.of(result.describe(source.plusMonths(1)))
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
