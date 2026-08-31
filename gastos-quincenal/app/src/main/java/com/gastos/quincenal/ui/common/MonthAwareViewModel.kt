package com.gastos.quincenal.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.domain.usecase.GetMonthOverviewUseCase
import com.gastos.quincenal.domain.usecase.MonthOverview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

/**
 * Base de los ViewModel que muestran un mes.
 *
 * Concentra la navegacion entre meses y la suscripcion al caso de uso, de modo
 * que cada pantalla solo se ocupa de su propio estado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MonthAwareViewModel(
    private val getMonthOverview: GetMonthOverviewUseCase
) : ViewModel() {

    private val _yearMonth = MutableStateFlow(YearMonth.now())
    val yearMonth: StateFlow<YearMonth> = _yearMonth.asStateFlow()

    /** Vista del mes seleccionado; `null` mientras se carga la primera emision. */
    val overview: StateFlow<MonthOverview?> = _yearMonth
        .flatMapLatest { month -> getMonthOverview(month) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
        )

    fun showPreviousMonth() {
        _yearMonth.value = _yearMonth.value.minusMonths(1)
    }

    fun showNextMonth() {
        _yearMonth.value = _yearMonth.value.plusMonths(1)
    }

    fun showToday() {
        _yearMonth.value = YearMonth.now()
    }

    fun showMonth(yearMonth: YearMonth) {
        _yearMonth.value = yearMonth
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
