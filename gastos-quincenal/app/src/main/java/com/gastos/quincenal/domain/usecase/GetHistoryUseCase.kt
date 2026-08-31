package com.gastos.quincenal.domain.usecase

import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.domain.calc.FinancialCalculator
import com.gastos.quincenal.domain.model.MonthHistoryEntry
import java.time.YearMonth

/**
 * Construye el historial de un ano completo evaluando las reglas de recurrencia
 * mes por mes, sin necesidad de que existan filas guardadas para cada mes.
 */
class GetHistoryUseCase(private val repository: BudgetRepository) {

    private val calculator = FinancialCalculator()

    suspend operator fun invoke(year: Int): List<MonthHistoryEntry> =
        (1..12).map { month ->
            val yearMonth = YearMonth.of(year, month)
            val data = repository.getMonth(yearMonth)
            MonthHistoryEntry(
                yearMonth = yearMonth,
                income = calculator.totalIncome(data.incomes),
                expenses = calculator.totalExpenses(data.expenses)
            )
        }
}
