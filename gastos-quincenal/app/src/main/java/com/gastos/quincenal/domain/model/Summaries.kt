package com.gastos.quincenal.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** Totales de una quincena. */
data class QuincenaSummary(
    val quincena: Quincena,
    val yearMonth: YearMonth,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val income: Money,
    val expenses: Money,
    val paidExpenses: Money,
    val pendingExpenses: Money
) {
    /** Sobrante de la quincena: ingresos menos gastos. Puede ser negativo. */
    val remaining: Money get() = income - expenses

    /** true cuando los gastos superan a los ingresos de la quincena. */
    val isOverBudget: Boolean get() = remaining.isNegative

    val dayRangeLabel: String get() = "${startDate.dayOfMonth} - ${endDate.dayOfMonth}"

    /** Porcentaje de los ingresos ya comprometido (0f..1f, 1f si no hay ingresos). */
    val usageRatio: Float
        get() = when {
            income.cents <= 0L -> if (expenses.isZero) 0f else 1f
            else -> (expenses.cents.toFloat() / income.cents.toFloat()).coerceIn(0f, 1f)
        }
}

/** Totales del mes completo mas el desglose de sus dos quincenas. */
data class MonthSummary(
    val yearMonth: YearMonth,
    val first: QuincenaSummary,
    val second: QuincenaSummary
) {
    val income: Money get() = first.income + second.income
    val expenses: Money get() = first.expenses + second.expenses
    val remaining: Money get() = income - expenses
    val paidExpenses: Money get() = first.paidExpenses + second.paidExpenses
    val pendingExpenses: Money get() = first.pendingExpenses + second.pendingExpenses
    val isOverBudget: Boolean get() = remaining.isNegative

    fun summaryFor(quincena: Quincena): QuincenaSummary =
        if (quincena == Quincena.PRIMERA) first else second
}

/** Semana natural del mes (domingo a sabado) recortada a los dias del propio mes. */
data class WeekSummary(
    val index: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val income: Money,
    val expenses: Money
) {
    val remaining: Money get() = income - expenses
    val label: String get() = "Semana $index"
    val rangeLabel: String get() = "${startDate.dayOfMonth} - ${endDate.dayOfMonth}"
}

/** Total gastado en una categoria dentro del periodo consultado. */
data class CategoryTotal(
    val categoryId: Long?,
    val categoryName: String,
    val colorArgb: Int,
    val total: Money,
    val count: Int
) {
    /** Proporcion respecto al total de gastos del periodo. */
    fun share(overall: Money): Float =
        if (overall.cents <= 0L) 0f else (total.cents.toFloat() / overall.cents.toFloat()).coerceIn(0f, 1f)
}

/** Fila del historial anual. */
data class MonthHistoryEntry(
    val yearMonth: YearMonth,
    val income: Money,
    val expenses: Money
) {
    val remaining: Money get() = income - expenses
}

/** Contenido de un dia del calendario. */
data class DaySummary(
    val date: LocalDate,
    val expenses: List<ExpenseOccurrence>,
    val incomes: List<IncomeOccurrence>
) {
    val totalExpenses: Money get() = expenses.sumOfMoney { it.amount }
    val totalIncome: Money get() = incomes.sumOfMoney { it.amount }
    val hasMovements: Boolean get() = expenses.isNotEmpty() || incomes.isNotEmpty()
    val quincena: Quincena get() = Quincena.of(date)
}

/** Aviso mostrado en el tablero cuando el presupuesto esta en riesgo. */
data class BudgetAlert(
    val severity: Severity,
    val title: String,
    val message: String,
    val income: Money,
    val expenses: Money
) {
    val difference: Money get() = income - expenses

    enum class Severity { WARNING, CAUTION }
}
