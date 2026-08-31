package com.gastos.quincenal.domain.usecase

import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.utils.DateUtils
import java.time.YearMonth

/** Que se copia al duplicar un mes. */
data class DuplicateOptions(
    val recurringExpenses: Boolean = true,
    val oneTimeExpenses: Boolean = true,
    val incomes: Boolean = true,
    val paidExpenses: Boolean = false,
    val keepCategories: Boolean = true
) {
    companion object {
        /**
         * Preset del boton "Copiar gastos al siguiente mes": respeta las reglas
         * de recurrencia y deja fuera los movimientos de un solo mes.
         */
        val ONLY_RECURRING = DuplicateOptions(
            recurringExpenses = true,
            oneTimeExpenses = false,
            incomes = false,
            paidExpenses = false,
            keepCategories = true
        )
    }
}

/** Cuantos movimientos se copiaron y cuantos se omitieron por ya existir. */
data class DuplicateResult(
    val expensesCopied: Int,
    val incomesCopied: Int,
    val skippedAlreadyPresent: Int
) {
    val total: Int get() = expensesCopied + incomesCopied

    fun describe(target: YearMonth): String {
        val month = DateUtils.monthTitleCapitalized(target)
        if (total == 0) {
            return if (skippedAlreadyPresent > 0) {
                "$month ya tenia todos esos movimientos ($skippedAlreadyPresent sin cambios)."
            } else {
                "No habia movimientos que copiar a $month."
            }
        }
        val parts = buildList {
            if (expensesCopied > 0) add("$expensesCopied gastos")
            if (incomesCopied > 0) add("$incomesCopied ingresos")
        }
        val suffix = if (skippedAlreadyPresent > 0) " ($skippedAlreadyPresent ya existian)" else ""
        return "Se copiaron ${parts.joinToString(" y ")} a $month$suffix."
    }
}

/**
 * Copia los movimientos de un mes a otro.
 *
 * Los gastos recurrentes ya aparecen solos en los meses siguientes porque la
 * aplicacion guarda la regla, no una fila por mes. Por eso esta operacion NO
 * vuelve a copiarlos cuando la regla ya cubre el mes destino: solo crea una
 * copia cuando el movimiento no llegaria por si mismo (por ejemplo, una regla
 * que ya termino, o un gasto de un solo mes que el usuario decide arrastrar).
 * Asi se evitan los duplicados que aparecerian al pulsar el boton dos veces.
 */
class DuplicateMonthUseCase(private val repository: BudgetRepository) {

    suspend operator fun invoke(
        source: YearMonth,
        target: YearMonth,
        options: DuplicateOptions
    ): DuplicateResult {
        val sourceData = repository.getMonth(source)
        val targetData = repository.getMonth(target)

        val existingExpenses = targetData.expenses.map { it.name.trim().lowercase() to it.amount.cents }.toSet()
        val existingIncomes = targetData.incomes.map { it.name.trim().lowercase() to it.amount.cents }.toSet()

        var expensesCopied = 0
        var incomesCopied = 0
        var skipped = 0

        sourceData.expenses.forEach { occurrence ->
            val allowedByKind = if (occurrence.isRecurring) {
                options.recurringExpenses
            } else {
                options.oneTimeExpenses
            }
            if (!allowedByKind) return@forEach
            if (occurrence.isPaid && !options.paidExpenses) return@forEach

            val key = occurrence.name.trim().lowercase() to occurrence.amount.cents
            if (key in existingExpenses) {
                skipped++
                return@forEach
            }

            repository.saveExpense(
                expense = ExpenseEntity(
                    name = occurrence.name,
                    amountCents = occurrence.amount.cents,
                    categoryId = occurrence.categoryId.takeIf { options.keepCategories },
                    date = DateUtils.clampDayOfMonth(target, occurrence.date.dayOfMonth),
                    type = occurrence.type,
                    isPaid = false,
                    notes = occurrence.notes
                ),
                rule = null
            )
            expensesCopied++
        }

        if (options.incomes) {
            sourceData.incomes.forEach { occurrence ->
                val key = occurrence.name.trim().lowercase() to occurrence.amount.cents
                if (key in existingIncomes) {
                    skipped++
                    return@forEach
                }

                repository.saveIncome(
                    income = IncomeEntity(
                        name = occurrence.name,
                        amountCents = occurrence.amount.cents,
                        date = DateUtils.clampDayOfMonth(target, occurrence.date.dayOfMonth),
                        notes = occurrence.notes
                    ),
                    rule = null
                )
                incomesCopied++
            }
        }

        return DuplicateResult(expensesCopied, incomesCopied, skipped)
    }
}
