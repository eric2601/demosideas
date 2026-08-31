package com.gastos.quincenal.domain.calc

import com.gastos.quincenal.domain.model.BudgetAlert
import com.gastos.quincenal.domain.model.CategoryTotal
import com.gastos.quincenal.domain.model.DaySummary
import com.gastos.quincenal.domain.model.ExpenseOccurrence
import com.gastos.quincenal.domain.model.IncomeOccurrence
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.MonthSummary
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.domain.model.QuincenaSummary
import com.gastos.quincenal.domain.model.WeekSummary
import com.gastos.quincenal.domain.model.sumOfMoney
import com.gastos.quincenal.utils.CurrencyFormatter
import java.time.LocalDate
import java.time.YearMonth

/**
 * Unico lugar donde se hacen los calculos financieros.
 *
 * Recibe las ocurrencias ya expandidas del mes y devuelve totales. No conoce Room,
 * Android ni Compose, de modo que toda la aritmetica del dinero queda cubierta por
 * pruebas unitarias y ninguna pantalla necesita repetir formulas.
 */
class FinancialCalculator(
    private val formatter: CurrencyFormatter = CurrencyFormatter.MXN
) {

    /** Suma de todos los ingresos de la lista. */
    fun totalIncome(incomes: List<IncomeOccurrence>): Money = incomes.sumOfMoney { it.amount }

    /** Suma de todos los gastos de la lista. */
    fun totalExpenses(expenses: List<ExpenseOccurrence>): Money = expenses.sumOfMoney { it.amount }

    /** Ingresos que caen dentro de la quincena indicada. */
    fun incomeOf(incomes: List<IncomeOccurrence>, quincena: Quincena): Money =
        incomes.filter { it.quincena == quincena }.sumOfMoney { it.amount }

    /** Gastos que caen dentro de la quincena indicada. */
    fun expensesOf(expenses: List<ExpenseOccurrence>, quincena: Quincena): Money =
        expenses.filter { it.quincena == quincena }.sumOfMoney { it.amount }

    /** Sobrante de una quincena: ingresos menos gastos. */
    fun remainingOf(
        incomes: List<IncomeOccurrence>,
        expenses: List<ExpenseOccurrence>,
        quincena: Quincena
    ): Money = incomeOf(incomes, quincena) - expensesOf(expenses, quincena)

    /** Sobrante del mes completo. */
    fun monthlyRemaining(
        incomes: List<IncomeOccurrence>,
        expenses: List<ExpenseOccurrence>
    ): Money = totalIncome(incomes) - totalExpenses(expenses)

    /** Resumen de una quincena con su desglose de pagado y pendiente. */
    fun quincenaSummary(
        yearMonth: YearMonth,
        quincena: Quincena,
        incomes: List<IncomeOccurrence>,
        expenses: List<ExpenseOccurrence>
    ): QuincenaSummary {
        val quincenaExpenses = expenses.filter { it.quincena == quincena }
        return QuincenaSummary(
            quincena = quincena,
            yearMonth = yearMonth,
            startDate = quincena.startDate(yearMonth),
            endDate = quincena.endDate(yearMonth),
            income = incomeOf(incomes, quincena),
            expenses = quincenaExpenses.sumOfMoney { it.amount },
            paidExpenses = quincenaExpenses.filter { it.isPaid }.sumOfMoney { it.amount },
            pendingExpenses = quincenaExpenses.filterNot { it.isPaid }.sumOfMoney { it.amount }
        )
    }

    /** Resumen completo del mes: ambas quincenas y los totales derivados. */
    fun monthSummary(
        yearMonth: YearMonth,
        incomes: List<IncomeOccurrence>,
        expenses: List<ExpenseOccurrence>
    ): MonthSummary = MonthSummary(
        yearMonth = yearMonth,
        first = quincenaSummary(yearMonth, Quincena.PRIMERA, incomes, expenses),
        second = quincenaSummary(yearMonth, Quincena.SEGUNDA, incomes, expenses)
    )

    /** Totales por semana natural del mes (domingo a sabado). */
    fun weeklySummaries(
        yearMonth: YearMonth,
        incomes: List<IncomeOccurrence>,
        expenses: List<ExpenseOccurrence>
    ): List<WeekSummary> = WeekCalculator.weeksOf(yearMonth).map { week ->
        WeekSummary(
            index = week.index,
            startDate = week.start,
            endDate = week.end,
            income = incomes.filter { week.contains(it.date) }.sumOfMoney { it.amount },
            expenses = expenses.filter { week.contains(it.date) }.sumOfMoney { it.amount }
        )
    }

    /** Gasto acumulado por categoria, de mayor a menor. */
    fun expensesByCategory(expenses: List<ExpenseOccurrence>): List<CategoryTotal> =
        expenses.groupBy { it.categoryId }
            .map { (categoryId, items) ->
                val first = items.first()
                CategoryTotal(
                    categoryId = categoryId,
                    categoryName = first.categoryName,
                    colorArgb = first.categoryColor,
                    total = items.sumOfMoney { it.amount },
                    count = items.size
                )
            }
            .sortedByDescending { it.total.cents }

    /** Gasto acumulado por quincena, util para las graficas comparativas. */
    fun expensesByQuincena(expenses: List<ExpenseOccurrence>): Map<Quincena, Money> = mapOf(
        Quincena.PRIMERA to expensesOf(expenses, Quincena.PRIMERA),
        Quincena.SEGUNDA to expensesOf(expenses, Quincena.SEGUNDA)
    )

    /** Agrupa las ocurrencias por dia para pintar el calendario. */
    fun dailyBreakdown(
        yearMonth: YearMonth,
        incomes: List<IncomeOccurrence>,
        expenses: List<ExpenseOccurrence>
    ): Map<LocalDate, DaySummary> {
        val expensesByDate = expenses.groupBy { it.date }
        val incomesByDate = incomes.groupBy { it.date }
        return (1..yearMonth.lengthOfMonth()).associate { day ->
            val date = yearMonth.atDay(day)
            date to DaySummary(
                date = date,
                expenses = expensesByDate[date].orEmpty().sortedByDescending { it.amount.cents },
                incomes = incomesByDate[date].orEmpty().sortedByDescending { it.amount.cents }
            )
        }
    }

    /**
     * Avisos de presupuesto.
     *
     * Se genera una advertencia por cada quincena cuyos gastos superen a los
     * ingresos y una precaucion cuando el sobrante queda por debajo del umbral
     * configurado por el usuario.
     */
    fun alerts(summary: MonthSummary, lowRemainingThreshold: Money): List<BudgetAlert> {
        val alerts = mutableListOf<BudgetAlert>()

        listOf(summary.first, summary.second).forEach { quincena ->
            when {
                quincena.isOverBudget -> alerts += BudgetAlert(
                    severity = BudgetAlert.Severity.WARNING,
                    title = "Gastos por encima de los ingresos",
                    message = "Tus gastos de la ${quincena.quincena.displayName.lowercase()} superan " +
                        "tus ingresos por ${formatter.format(quincena.remaining.abs())}.",
                    income = quincena.income,
                    expenses = quincena.expenses
                )

                quincena.income.isPositive && quincena.remaining < lowRemainingThreshold -> alerts += BudgetAlert(
                    severity = BudgetAlert.Severity.CAUTION,
                    title = "Sobrante bajo",
                    message = "Te queda poco margen en la ${quincena.quincena.displayName.lowercase()}: " +
                        "${formatter.format(quincena.remaining)}.",
                    income = quincena.income,
                    expenses = quincena.expenses
                )
            }
        }

        if (summary.isOverBudget) {
            alerts += BudgetAlert(
                severity = BudgetAlert.Severity.WARNING,
                title = "El mes cierra en numeros rojos",
                message = "Los gastos del mes superan a los ingresos por " +
                    "${formatter.format(summary.remaining.abs())}.",
                income = summary.income,
                expenses = summary.expenses
            )
        }

        return alerts
    }
}
