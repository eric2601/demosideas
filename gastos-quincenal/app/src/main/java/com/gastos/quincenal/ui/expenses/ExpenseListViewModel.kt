package com.gastos.quincenal.ui.expenses

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

/** Filtro activo en la lista de gastos. */
enum class ExpenseFilter {
    ALL,
    PENDING,
    PAID,
    RECURRING;

    val displayName: String
        get() = when (this) {
            ALL -> "Todos"
            PENDING -> "Pendientes"
            PAID -> "Pagados"
            RECURRING -> "Recurrentes"
        }
}

/** Lista de gastos del mes agrupada por quincena, con acciones por ocurrencia. */
class ExpenseListViewModel(
    getMonthOverview: GetMonthOverviewUseCase,
    private val budgetRepository: BudgetRepository
) : MonthAwareViewModel(getMonthOverview) {

    private val _filter = MutableStateFlow(ExpenseFilter.ALL)
    val filter: StateFlow<ExpenseFilter> = _filter.asStateFlow()

    private val _categoryFilter = MutableStateFlow<Long?>(null)
    val categoryFilter: StateFlow<Long?> = _categoryFilter.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    fun setFilter(filter: ExpenseFilter) {
        _filter.value = filter
    }

    fun setCategoryFilter(categoryId: Long?) {
        _categoryFilter.value = if (_categoryFilter.value == categoryId) null else categoryId
    }

    fun togglePaid(expenseId: Long, date: LocalDate, paid: Boolean) {
        viewModelScope.launch {
            budgetRepository.setExpensePaid(expenseId, date, paid)
            _message.value = UiMessage.of(if (paid) "Marcado como pagado." else "Marcado como pendiente.")
        }
    }

    fun duplicate(expenseId: Long) {
        viewModelScope.launch {
            budgetRepository.duplicateExpense(expenseId)
            _message.value = UiMessage.of("Gasto duplicado.")
        }
    }

    /** Borra la serie completa junto con su regla de recurrencia. */
    fun deleteSeries(expenseId: Long) {
        viewModelScope.launch {
            budgetRepository.deleteExpense(expenseId)
            _message.value = UiMessage.of("Gasto eliminado.")
        }
    }

    /** Quita solo la ocurrencia de esta fecha y conserva el resto de la serie. */
    fun deleteOccurrence(expenseId: Long, date: LocalDate) {
        viewModelScope.launch {
            budgetRepository.skipExpenseOccurrence(expenseId, date)
            _message.value = UiMessage.of("Se quito el gasto de esta fecha.")
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
