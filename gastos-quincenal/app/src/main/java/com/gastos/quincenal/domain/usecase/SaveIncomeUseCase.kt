package com.gastos.quincenal.domain.usecase

import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.RecurrenceEnd
import java.time.DayOfWeek
import java.time.LocalDate

/** Datos validados que llegan del formulario de ingreso. */
data class IncomeInput(
    val id: Long = 0L,
    val name: String,
    val amount: Money,
    val date: LocalDate,
    val frequency: Frequency = Frequency.ONCE,
    val recurrenceEnd: RecurrenceEnd = RecurrenceEnd.ONLY_THIS_MONTH,
    val endDate: LocalDate? = null,
    val dayOfWeek: DayOfWeek? = null,
    val interval: Int = 1,
    val isReceived: Boolean = false,
    val notes: String = ""
)

/** Valida y guarda un ingreso con su regla de recurrencia. */
class SaveIncomeUseCase(private val repository: BudgetRepository) {

    suspend operator fun invoke(input: IncomeInput): SaveResult {
        val errors = validate(input)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val rule = SaveExpenseUseCase.buildRule(
            frequency = input.frequency,
            recurrenceEnd = input.recurrenceEnd,
            date = input.date,
            endDate = input.endDate,
            dayOfWeek = input.dayOfWeek,
            interval = input.interval
        )

        val id = repository.saveIncome(
            income = IncomeEntity(
                id = input.id,
                name = input.name.trim(),
                amountCents = input.amount.cents,
                date = input.date,
                isReceived = input.isReceived,
                notes = input.notes.trim()
            ),
            rule = rule
        )
        return SaveResult.Success(id)
    }

    private fun validate(input: IncomeInput): List<String> = buildList {
        if (input.name.isBlank()) add("Escribe un concepto para el ingreso.")
        if (input.name.length > SaveExpenseUseCase.MAX_NAME_LENGTH) add("El concepto es demasiado largo.")
        if (input.amount.cents <= 0L) add("La cantidad debe ser mayor que cero.")
        if (input.recurrenceEnd == RecurrenceEnd.UNTIL_DATE) {
            when {
                input.endDate == null -> add("Elige la fecha hasta la que se repite.")
                input.endDate.isBefore(input.date) ->
                    add("La fecha final no puede ser anterior a la fecha del ingreso.")
            }
        }
        if (input.interval < 1) add("El intervalo de repeticion debe ser al menos 1.")
    }
}
