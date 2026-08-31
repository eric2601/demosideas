package com.gastos.quincenal.domain

import com.gastos.quincenal.domain.model.ExpenseOccurrence
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.IncomeOccurrence
import com.gastos.quincenal.domain.model.Money
import java.time.LocalDate

/** Constructores breves para no repetir datos en las pruebas. */
object TestFixtures {

    fun expense(
        day: Int,
        pesos: Double,
        name: String = "Gasto",
        categoryId: Long? = 1L,
        categoryName: String = "Comida",
        paid: Boolean = false,
        year: Int = 2026,
        month: Int = 1
    ) = ExpenseOccurrence(
        expenseId = day * 1000L + pesos.toLong(),
        date = LocalDate.of(year, month, day),
        name = name,
        amount = Money.ofPesos(pesos),
        categoryId = categoryId,
        categoryName = categoryName,
        categoryColor = 0xFF000000.toInt(),
        type = ExpenseType.VARIABLE,
        isPaid = paid,
        notes = "",
        recurrence = null
    )

    fun income(
        day: Int,
        pesos: Double,
        name: String = "Sueldo",
        year: Int = 2026,
        month: Int = 1
    ) = IncomeOccurrence(
        incomeId = day * 1000L + pesos.toLong(),
        date = LocalDate.of(year, month, day),
        name = name,
        amount = Money.ofPesos(pesos),
        notes = "",
        recurrence = null,
        isReceived = false
    )
}
