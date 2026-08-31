package com.gastos.quincenal.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gastos.quincenal.data.local.AppDatabase
import com.gastos.quincenal.data.local.DefaultCategories
import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.domain.calc.FinancialCalculator
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.domain.recurrence.RecurrenceRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pruebas de la capa de datos sobre una base en memoria: comprueban que las
 * reglas de recurrencia se expanden bien y que las excepciones por ocurrencia
 * no contaminan los demas meses.
 */
@RunWith(AndroidJUnit4::class)
class BudgetRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: BudgetRepository
    private val calculator = FinancialCalculator()

    @Before
    fun setUp() = runTest {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repository = BudgetRepository(db)
        db.categoryDao().insertAll(DefaultCategories.ALL)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun un_gasto_de_un_solo_mes_no_aparece_en_el_mes_siguiente() = runTest {
        repository.saveExpense(
            expense = ExpenseEntity(
                name = "Tenencia carro",
                amountCents = Money.ofPesos(1_000.0).cents,
                categoryId = null,
                date = LocalDate.of(2026, 1, 28)
            ),
            rule = null
        )

        assertEquals(1, repository.getMonth(YearMonth.of(2026, 1)).expenses.size)
        assertTrue(repository.getMonth(YearMonth.of(2026, 2)).expenses.isEmpty())
    }

    @Test
    fun un_gasto_mensual_aparece_en_todos_los_meses_sin_duplicar_filas() = runTest {
        repository.saveExpense(
            expense = ExpenseEntity(
                name = "Internet",
                amountCents = Money.ofPesos(500.0).cents,
                categoryId = null,
                date = LocalDate.of(2026, 1, 5)
            ),
            rule = RecurrenceRule(
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 5),
                dayOfMonth = 5
            )
        )

        listOf(1, 2, 3, 4).forEach { month ->
            val data = repository.getMonth(YearMonth.of(2026, month))
            assertEquals(1, data.expenses.size)
            assertEquals(Money.ofPesos(500.0), data.expenses.first().amount)
        }

        // Una sola fila almacenada, aunque se muestre en cuatro meses.
        assertEquals(1, db.expenseDao().getAll().size)
    }

    @Test
    fun marcar_pagado_solo_afecta_a_la_ocurrencia_indicada() = runTest {
        val id = repository.saveExpense(
            expense = ExpenseEntity(
                name = "Renta",
                amountCents = Money.ofPesos(8_000.0).cents,
                categoryId = null,
                date = LocalDate.of(2026, 1, 1)
            ),
            rule = RecurrenceRule(
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                dayOfMonth = 1
            )
        )

        repository.setExpensePaid(id, LocalDate.of(2026, 2, 1), paid = true)

        assertTrue(repository.getMonth(YearMonth.of(2026, 2)).expenses.first().isPaid)
        assertFalse(repository.getMonth(YearMonth.of(2026, 1)).expenses.first().isPaid)
        assertFalse(repository.getMonth(YearMonth.of(2026, 3)).expenses.first().isPaid)
    }

    @Test
    fun cambiar_el_importe_de_una_ocurrencia_no_altera_la_serie() = runTest {
        val id = repository.saveExpense(
            expense = ExpenseEntity(
                name = "Luz",
                amountCents = Money.ofPesos(600.0).cents,
                categoryId = null,
                date = LocalDate.of(2026, 1, 20)
            ),
            rule = RecurrenceRule(
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 20),
                dayOfMonth = 20
            )
        )

        repository.setExpenseAmountForOccurrence(
            expenseId = id,
            date = LocalDate.of(2026, 3, 20),
            cents = Money.ofPesos(950.0).cents
        )

        assertEquals(
            Money.ofPesos(950.0),
            repository.getMonth(YearMonth.of(2026, 3)).expenses.first().amount
        )
        assertEquals(
            Money.ofPesos(600.0),
            repository.getMonth(YearMonth.of(2026, 4)).expenses.first().amount
        )
    }

    @Test
    fun omitir_una_ocurrencia_conserva_el_resto_de_la_serie() = runTest {
        val id = repository.saveExpense(
            expense = ExpenseEntity(
                name = "Gimnasio",
                amountCents = Money.ofPesos(450.0).cents,
                categoryId = null,
                date = LocalDate.of(2026, 1, 3)
            ),
            rule = RecurrenceRule(
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 3),
                dayOfMonth = 3
            )
        )

        repository.skipExpenseOccurrence(id, LocalDate.of(2026, 2, 3))

        assertTrue(repository.getMonth(YearMonth.of(2026, 2)).expenses.isEmpty())
        assertEquals(1, repository.getMonth(YearMonth.of(2026, 3)).expenses.size)
    }

    @Test
    fun editar_un_gasto_recurrente_conserva_los_pagos_marcados() = runTest {
        val id = repository.saveExpense(
            expense = ExpenseEntity(
                name = "Netflix",
                amountCents = Money.ofPesos(299.0).cents,
                categoryId = null,
                date = LocalDate.of(2026, 1, 8)
            ),
            rule = RecurrenceRule(
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 8),
                dayOfMonth = 8
            )
        )
        repository.setExpensePaid(id, LocalDate.of(2026, 1, 8), paid = true)

        val stored = requireNotNull(repository.getExpense(id))
        repository.saveExpense(
            expense = stored.copy(name = "Netflix Premium"),
            rule = RecurrenceRule(
                id = stored.recurrenceId ?: 0L,
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 8),
                dayOfMonth = 8
            )
        )

        val january = repository.getMonth(YearMonth.of(2026, 1)).expenses.first()
        assertEquals("Netflix Premium", january.name)
        assertTrue("La excepcion de pago debe sobrevivir a la edicion", january.isPaid)
    }

    @Test
    fun los_totales_por_quincena_se_calculan_sobre_las_ocurrencias_expandidas() = runTest {
        repository.saveIncome(
            income = IncomeEntity(
                name = "Sueldo",
                amountCents = Money.ofPesos(13_600.0).cents,
                date = LocalDate.of(2026, 1, 15)
            ),
            rule = null
        )
        repository.saveExpense(
            expense = ExpenseEntity(
                name = "Despensa",
                amountCents = Money.ofPesos(2_600.0).cents,
                categoryId = null,
                date = LocalDate.of(2026, 1, 2)
            ),
            rule = null
        )
        repository.saveExpense(
            expense = ExpenseEntity(
                name = "Gasolina",
                amountCents = Money.ofPesos(900.0).cents,
                categoryId = null,
                date = LocalDate.of(2026, 1, 24)
            ),
            rule = null
        )

        val data = repository.getMonth(YearMonth.of(2026, 1))
        val summary = calculator.monthSummary(YearMonth.of(2026, 1), data.incomes, data.expenses)

        assertEquals(Money.ofPesos(2_600.0), summary.first.expenses)
        assertEquals(Money.ofPesos(11_000.0), summary.first.remaining)
        assertEquals(Money.ofPesos(900.0), summary.second.expenses)
        assertEquals(Quincena.SEGUNDA, data.expenses.last().quincena)
    }
}
