package com.gastos.quincenal.ui.calendar

import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.domain.usecase.GetMonthOverviewUseCase
import com.gastos.quincenal.ui.common.MonthAwareViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Estado del calendario: mes visible y dia seleccionado. */
class CalendarViewModel(
    getMonthOverview: GetMonthOverviewUseCase,
    private val budgetRepository: BudgetRepository
) : MonthAwareViewModel(getMonthOverview) {

    private val _selectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    fun selectDate(date: LocalDate) {
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }

    /** Al cambiar de mes se limpia la seleccion para no mostrar un dia ajeno. */
    fun onMonthChanged() {
        _selectedDate.value = null
    }

    fun togglePaid(expenseId: Long, date: LocalDate, paid: Boolean) {
        viewModelScope.launch {
            budgetRepository.setExpensePaid(expenseId, date, paid)
        }
    }
}
