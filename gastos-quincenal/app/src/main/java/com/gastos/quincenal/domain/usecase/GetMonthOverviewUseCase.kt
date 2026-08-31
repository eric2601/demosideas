package com.gastos.quincenal.domain.usecase

import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.data.repository.SettingsRepository
import com.gastos.quincenal.domain.calc.FinancialCalculator
import com.gastos.quincenal.domain.model.BudgetAlert
import com.gastos.quincenal.domain.model.Category
import com.gastos.quincenal.domain.model.CategoryTotal
import com.gastos.quincenal.domain.model.DaySummary
import com.gastos.quincenal.domain.model.ExpenseOccurrence
import com.gastos.quincenal.domain.model.IncomeOccurrence
import com.gastos.quincenal.domain.model.MonthSummary
import com.gastos.quincenal.domain.model.WeekSummary
import com.gastos.quincenal.utils.CurrencyFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth

/** Vista completa de un mes: movimientos, totales, semanas, categorias y avisos. */
data class MonthOverview(
    val yearMonth: YearMonth,
    val summary: MonthSummary,
    val expenses: List<ExpenseOccurrence>,
    val incomes: List<IncomeOccurrence>,
    val weeks: List<WeekSummary>,
    val byCategory: List<CategoryTotal>,
    val byDay: Map<LocalDate, DaySummary>,
    val alerts: List<BudgetAlert>,
    val categories: List<Category>,
    val formatter: CurrencyFormatter
)

/**
 * Reune en un solo flujo todo lo que las pantallas de un mes necesitan.
 * Concentrar aqui la composicion evita que cada ViewModel repita los calculos.
 */
class GetMonthOverviewUseCase(
    private val budgetRepository: BudgetRepository,
    private val settingsRepository: SettingsRepository
) {

    operator fun invoke(yearMonth: YearMonth): Flow<MonthOverview> = combine(
        budgetRepository.observeMonth(yearMonth),
        settingsRepository.observeSettings()
    ) { month, settings ->
        val formatter = settings.formatter()
        val calculator = FinancialCalculator(formatter)
        val summary = calculator.monthSummary(yearMonth, month.incomes, month.expenses)

        MonthOverview(
            yearMonth = yearMonth,
            summary = summary,
            expenses = month.expenses,
            incomes = month.incomes,
            weeks = calculator.weeklySummaries(yearMonth, month.incomes, month.expenses),
            byCategory = calculator.expensesByCategory(month.expenses),
            byDay = calculator.dailyBreakdown(yearMonth, month.incomes, month.expenses),
            alerts = calculator.alerts(summary, settings.lowRemainingThreshold),
            categories = month.categories,
            formatter = formatter
        )
    }
}
