package com.gastos.quincenal.data.repository

import com.gastos.quincenal.data.local.DefaultCategories
import com.gastos.quincenal.data.local.entity.CategoryEntity
import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.ExpenseOverrideEntity
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.local.entity.IncomeOverrideEntity
import com.gastos.quincenal.data.local.entity.RecurrenceEntity
import com.gastos.quincenal.data.mapper.toDomain
import com.gastos.quincenal.domain.model.ExpenseOccurrence
import com.gastos.quincenal.domain.model.IncomeOccurrence
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.recurrence.RecurrenceEngine
import com.gastos.quincenal.domain.recurrence.RecurrenceRule
import com.gastos.quincenal.utils.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/**
 * Convierte las definiciones almacenadas (gasto/ingreso + regla de recurrencia)
 * en las ocurrencias concretas de un mes, aplicando las excepciones guardadas
 * para ocurrencias individuales.
 *
 * Es la pieza que permite NO duplicar filas mes a mes: la base guarda la regla,
 * esta clase produce las fechas cuando la pantalla las necesita.
 */
object OccurrenceExpander {

    fun expandExpenses(
        yearMonth: YearMonth,
        expenses: List<ExpenseEntity>,
        recurrences: Map<Long, RecurrenceEntity>,
        categories: Map<Long, CategoryEntity>,
        overrides: List<ExpenseOverrideEntity>
    ): List<ExpenseOccurrence> {
        val overrideIndex = overrides.associateBy { it.expenseId to it.occurrenceDate }
        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()
        val result = mutableListOf<ExpenseOccurrence>()

        expenses.forEach { expense ->
            val rule = expense.recurrenceId?.let { recurrences[it]?.toDomain() }
            val dates = datesFor(expense.date, rule, yearMonth)

            dates.forEach { baseDate ->
                val override = overrideIndex[expense.id to baseDate]
                if (override?.skipped == true) return@forEach

                val effectiveDate = override?.movedToDate ?: baseDate
                // Una ocurrencia movida fuera del mes deja de pertenecer a este mes.
                if (effectiveDate.isBefore(monthStart) || effectiveDate.isAfter(monthEnd)) return@forEach

                val category = expense.categoryId?.let { categories[it] }
                result += ExpenseOccurrence(
                    expenseId = expense.id,
                    date = effectiveDate,
                    name = expense.name,
                    amount = Money(override?.amountCents ?: expense.amountCents),
                    categoryId = expense.categoryId,
                    categoryName = category?.name ?: DefaultCategories.FALLBACK_NAME,
                    categoryColor = category?.colorArgb ?: DefaultCategories.FALLBACK_COLOR,
                    type = expense.type,
                    isPaid = override?.isPaid ?: expense.isPaid,
                    notes = override?.notes ?: expense.notes,
                    recurrence = rule,
                    hasOverride = override != null
                )
            }
        }

        return result.sortedWith(compareBy({ it.date }, { -it.amount.cents }, { it.name }))
    }

    fun expandIncomes(
        yearMonth: YearMonth,
        incomes: List<IncomeEntity>,
        recurrences: Map<Long, RecurrenceEntity>,
        overrides: List<IncomeOverrideEntity>
    ): List<IncomeOccurrence> {
        val overrideIndex = overrides.associateBy { it.incomeId to it.occurrenceDate }
        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()
        val result = mutableListOf<IncomeOccurrence>()

        incomes.forEach { income ->
            val rule = income.recurrenceId?.let { recurrences[it]?.toDomain() }
            val dates = datesFor(income.date, rule, yearMonth)

            dates.forEach { baseDate ->
                val override = overrideIndex[income.id to baseDate]
                if (override?.skipped == true) return@forEach

                val effectiveDate = override?.movedToDate ?: baseDate
                if (effectiveDate.isBefore(monthStart) || effectiveDate.isAfter(monthEnd)) return@forEach

                result += IncomeOccurrence(
                    incomeId = income.id,
                    date = effectiveDate,
                    name = income.name,
                    amount = Money(override?.amountCents ?: income.amountCents),
                    notes = override?.notes ?: income.notes,
                    recurrence = rule,
                    isReceived = override?.isReceived ?: income.isReceived,
                    hasOverride = override != null
                )
            }
        }

        return result.sortedWith(compareBy({ it.date }, { -it.amount.cents }, { it.name }))
    }

    /**
     * Fechas que una definicion aporta al mes: la propia fecha si es un
     * movimiento unico, o las que genere la regla si es recurrente.
     */
    private fun datesFor(
        anchorDate: LocalDate,
        rule: RecurrenceRule?,
        yearMonth: YearMonth
    ): List<LocalDate> = when {
        rule == null ->
            if (DateUtils.yearMonthOf(anchorDate) == yearMonth) listOf(anchorDate) else emptyList()

        else -> RecurrenceEngine.occurrencesInMonth(rule, yearMonth)
    }
}
