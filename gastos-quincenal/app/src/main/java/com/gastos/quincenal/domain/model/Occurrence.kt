package com.gastos.quincenal.domain.model

import com.gastos.quincenal.domain.recurrence.RecurrenceRule
import java.time.LocalDate

/**
 * Un gasto tal como se ve en un dia concreto del calendario.
 *
 * Puede provenir de:
 *  - un gasto unico (la ocurrencia coincide con su propia fecha), o
 *  - una regla de recurrencia expandida para el mes consultado.
 *
 * Los cambios puntuales (marcar pagado, cambiar el importe de un solo mes,
 * omitir una ocurrencia) se guardan como excepciones y ya vienen aplicados aqui.
 */
data class ExpenseOccurrence(
    val expenseId: Long,
    val date: LocalDate,
    val name: String,
    val amount: Money,
    val categoryId: Long?,
    val categoryName: String,
    val categoryColor: Int,
    val type: ExpenseType,
    val isPaid: Boolean,
    val notes: String,
    val recurrence: RecurrenceRule?,
    /** true cuando el importe o la fecha fueron ajustados solo para esta ocurrencia. */
    val hasOverride: Boolean = false
) {
    val quincena: Quincena get() = Quincena.of(date)
    val isRecurring: Boolean get() = recurrence != null
    val status: PaymentStatus get() = if (isPaid) PaymentStatus.PAID else PaymentStatus.PENDING

    /** Identificador estable de la ocurrencia (gasto + fecha). */
    val occurrenceKey: String get() = "$expenseId@$date"
}

/** Un ingreso tal como se ve en un dia concreto del calendario. */
data class IncomeOccurrence(
    val incomeId: Long,
    val date: LocalDate,
    val name: String,
    val amount: Money,
    val notes: String,
    val recurrence: RecurrenceRule?,
    val isReceived: Boolean,
    val hasOverride: Boolean = false
) {
    val quincena: Quincena get() = Quincena.of(date)
    val isRecurring: Boolean get() = recurrence != null
    val occurrenceKey: String get() = "$incomeId@$date"
}
