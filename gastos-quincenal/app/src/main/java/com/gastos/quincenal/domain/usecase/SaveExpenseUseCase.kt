package com.gastos.quincenal.domain.usecase

import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.RecurrenceEnd
import com.gastos.quincenal.domain.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate

/** Datos validados que llegan del formulario de gasto. */
data class ExpenseInput(
    val id: Long = 0L,
    val name: String,
    val amount: Money,
    val categoryId: Long?,
    val date: LocalDate,
    val type: ExpenseType = ExpenseType.VARIABLE,
    val frequency: Frequency = Frequency.ONCE,
    val recurrenceEnd: RecurrenceEnd = RecurrenceEnd.ONLY_THIS_MONTH,
    val endDate: LocalDate? = null,
    val dayOfWeek: DayOfWeek? = null,
    val interval: Int = 1,
    val isPaid: Boolean = false,
    val notes: String = ""
)

/** Resultado de intentar guardar: el id creado o la lista de errores. */
sealed interface SaveResult {
    data class Success(val id: Long) : SaveResult
    data class Invalid(val errors: List<String>) : SaveResult
}

/**
 * Valida y guarda un gasto, construyendo la regla de recurrencia que le
 * corresponde a partir de la frecuencia y el tipo de fin elegidos.
 */
class SaveExpenseUseCase(private val repository: BudgetRepository) {

    suspend operator fun invoke(input: ExpenseInput): SaveResult {
        val errors = validate(input)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val rule = buildRule(
            frequency = input.frequency,
            recurrenceEnd = input.recurrenceEnd,
            date = input.date,
            endDate = input.endDate,
            dayOfWeek = input.dayOfWeek,
            interval = input.interval
        )

        val id = repository.saveExpense(
            expense = ExpenseEntity(
                id = input.id,
                name = input.name.trim(),
                amountCents = input.amount.cents,
                categoryId = input.categoryId,
                date = input.date,
                type = input.type,
                isPaid = input.isPaid,
                notes = input.notes.trim()
            ),
            rule = rule
        )
        return SaveResult.Success(id)
    }

    private fun validate(input: ExpenseInput): List<String> = buildList {
        if (input.name.isBlank()) add("Escribe un nombre para el gasto.")
        if (input.name.length > MAX_NAME_LENGTH) add("El nombre es demasiado largo.")
        if (input.amount.cents <= 0L) add("La cantidad debe ser mayor que cero.")
        if (input.recurrenceEnd == RecurrenceEnd.UNTIL_DATE) {
            when {
                input.endDate == null -> add("Elige la fecha hasta la que se repite.")
                input.endDate.isBefore(input.date) ->
                    add("La fecha final no puede ser anterior a la fecha del gasto.")
            }
        }
        if (input.frequency.needsDayOfWeek && input.recurrenceEnd != RecurrenceEnd.ONLY_THIS_MONTH &&
            input.dayOfWeek == null
        ) {
            add("Elige el dia de la semana en que se repite.")
        }
        if (input.interval < 1) add("El intervalo de repeticion debe ser al menos 1.")
    }

    companion object {
        const val MAX_NAME_LENGTH = 80

        /**
         * Traduce la eleccion del formulario a una [RecurrenceRule].
         *
         * Devuelve `null` cuando el movimiento es de un solo mes: en ese caso no
         * se guarda ninguna regla y el gasto no reaparece en meses siguientes.
         */
        fun buildRule(
            frequency: Frequency,
            recurrenceEnd: RecurrenceEnd,
            date: LocalDate,
            endDate: LocalDate?,
            dayOfWeek: DayOfWeek?,
            interval: Int
        ): RecurrenceRule? {
            if (recurrenceEnd == RecurrenceEnd.ONLY_THIS_MONTH) {
                // "Solo este mes" con frecuencia semanal si tiene sentido repetirlo
                // dentro del propio mes; en cualquier otro caso, un unico movimiento.
                if (frequency != Frequency.WEEKLY && frequency != Frequency.BIWEEKLY) return null
                val lastDayOfMonth = date.withDayOfMonth(date.lengthOfMonth())
                return RecurrenceRule(
                    frequency = frequency,
                    interval = interval,
                    startDate = date,
                    endDate = lastDayOfMonth,
                    dayOfWeek = dayOfWeek ?: date.dayOfWeek,
                    active = true
                )
            }

            if (frequency == Frequency.ONCE) return null

            val effectiveEnd = if (recurrenceEnd == RecurrenceEnd.UNTIL_DATE) endDate else null
            return RecurrenceRule(
                frequency = frequency,
                interval = interval,
                startDate = date,
                endDate = effectiveEnd,
                dayOfMonth = if (frequency == Frequency.MONTHLY || frequency == Frequency.YEARLY) {
                    date.dayOfMonth
                } else {
                    null
                },
                dayOfWeek = if (frequency.needsDayOfWeek) dayOfWeek ?: date.dayOfWeek else null,
                active = true
            )
        }
    }
}
