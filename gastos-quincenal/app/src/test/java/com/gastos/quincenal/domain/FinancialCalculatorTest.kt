package com.gastos.quincenal.domain

import com.gastos.quincenal.domain.calc.FinancialCalculator
import com.gastos.quincenal.domain.model.BudgetAlert
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.Quincena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/**
 * Reproduce el desglose de la hoja de referencia para comprobar que los totales
 * y los sobrantes se calculan exactamente.
 *
 * Nota sobre las cifras de referencia: la hoja original anota "TOTAL GASTOS
 * 12,434" en la primera quincena y "8,954" en la segunda, pero la suma de los
 * conceptos que enumera da 12,014 y 8,554 (faltan 420 y 400 respectivamente,
 * seguramente por renglones que no se transcribieron). Las pruebas verifican la
 * suma real de los conceptos, que es lo que la aplicacion debe mostrar: sumar de
 * mas para cuadrar con un total anotado a mano seria justo el error que este
 * proyecto pretende evitar.
 */
class FinancialCalculatorTest {

    private val calculator = FinancialCalculator()
    private val january = YearMonth.of(2026, 1)

    private val incomes = listOf(
        TestFixtures.income(day = 15, pesos = 13_600.0),
        TestFixtures.income(day = 30, pesos = 9_722.0)
    )

    private val expenses = listOf(
        // Primera quincena (conceptos de la hoja de referencia): suman 12,014
        TestFixtures.expense(day = 2, pesos = 2_600.0),
        TestFixtures.expense(day = 5, pesos = 600.0),
        TestFixtures.expense(day = 10, pesos = 900.0, name = "Gasolina", categoryId = 2L, categoryName = "Gasolina"),
        TestFixtures.expense(day = 8, pesos = 210.0, name = "Movi"),
        TestFixtures.expense(day = 12, pesos = 840.0, name = "Elektra Ab"),
        TestFixtures.expense(day = 12, pesos = 500.0, name = "Elektra"),
        TestFixtures.expense(day = 12, pesos = 800.0, name = "Elektra"),
        TestFixtures.expense(day = 14, pesos = 700.0, name = "Verificacion"),
        TestFixtures.expense(day = 15, pesos = 1_114.0, name = "Creditea"),
        TestFixtures.expense(day = 15, pesos = 1_500.0, name = "Control"),
        TestFixtures.expense(day = 15, pesos = 2_250.0, name = "Papa"),
        // Segunda quincena (conceptos de la hoja de referencia): suman 8,554
        TestFixtures.expense(day = 17, pesos = 2_800.0),
        TestFixtures.expense(day = 20, pesos = 600.0),
        TestFixtures.expense(day = 24, pesos = 900.0, name = "Gasolina", categoryId = 2L, categoryName = "Gasolina"),
        TestFixtures.expense(day = 26, pesos = 840.0, name = "Elektra Ab"),
        TestFixtures.expense(day = 26, pesos = 500.0, name = "Elektra"),
        TestFixtures.expense(day = 26, pesos = 800.0, name = "Elektra"),
        TestFixtures.expense(day = 28, pesos = 1_000.0, name = "Tenencia Carro"),
        TestFixtures.expense(day = 30, pesos = 1_114.0, name = "Creditea")
    )

    @Test
    fun `los totales del mes suman exactamente los conceptos registrados`() {
        assertEquals(Money.ofPesos(23_322.0), calculator.totalIncome(incomes))
        assertEquals(Money.ofPesos(20_568.0), calculator.totalExpenses(expenses))
        assertEquals(Money.ofPesos(2_754.0), calculator.monthlyRemaining(incomes, expenses))
    }

    @Test
    fun `el total del mes es la suma de las dos quincenas`() {
        val summary = calculator.monthSummary(january, incomes, expenses)

        assertEquals(summary.first.expenses + summary.second.expenses, summary.expenses)
        assertEquals(summary.first.income + summary.second.income, summary.income)
        assertEquals(summary.first.remaining + summary.second.remaining, summary.remaining)
    }

    @Test
    fun `la primera quincena suma 12014 y deja 1586 de sobrante`() {
        assertEquals(Money.ofPesos(13_600.0), calculator.incomeOf(incomes, Quincena.PRIMERA))
        assertEquals(Money.ofPesos(12_014.0), calculator.expensesOf(expenses, Quincena.PRIMERA))
        assertEquals(
            Money.ofPesos(1_586.0),
            calculator.remainingOf(incomes, expenses, Quincena.PRIMERA)
        )
    }

    @Test
    fun `la segunda quincena suma 8554 y deja 1168 de sobrante`() {
        assertEquals(Money.ofPesos(9_722.0), calculator.incomeOf(incomes, Quincena.SEGUNDA))
        assertEquals(Money.ofPesos(8_554.0), calculator.expensesOf(expenses, Quincena.SEGUNDA))
        assertEquals(
            Money.ofPesos(1_168.0),
            calculator.remainingOf(incomes, expenses, Quincena.SEGUNDA)
        )
    }

    @Test
    fun `el resumen del mes reparte correctamente las dos quincenas`() {
        val summary = calculator.monthSummary(january, incomes, expenses)

        assertEquals(Money.ofPesos(1_586.0), summary.first.remaining)
        assertEquals(Money.ofPesos(1_168.0), summary.second.remaining)
        assertEquals(summary.income - summary.expenses, summary.remaining)
        assertFalse(summary.isOverBudget)
        assertEquals(15, summary.first.endDate.dayOfMonth)
        assertEquals(31, summary.second.endDate.dayOfMonth)
    }

    @Test
    fun `pagado y pendiente se contabilizan por separado`() {
        val mixed = listOf(
            TestFixtures.expense(day = 3, pesos = 1_000.0, paid = true),
            TestFixtures.expense(day = 4, pesos = 500.0, paid = false)
        )
        val summary = calculator.quincenaSummary(january, Quincena.PRIMERA, emptyList(), mixed)

        assertEquals(Money.ofPesos(1_500.0), summary.expenses)
        assertEquals(Money.ofPesos(1_000.0), summary.paidExpenses)
        assertEquals(Money.ofPesos(500.0), summary.pendingExpenses)
    }

    @Test
    fun `las semanas del mes suman lo mismo que el total`() {
        val weeks = calculator.weeklySummaries(january, incomes, expenses)
        val weeklyExpenses = Money(weeks.sumOf { it.expenses.cents })
        val weeklyIncome = Money(weeks.sumOf { it.income.cents })

        assertEquals(calculator.totalExpenses(expenses), weeklyExpenses)
        assertEquals(calculator.totalIncome(incomes), weeklyIncome)
    }

    @Test
    fun `el gasto por categoria se ordena de mayor a menor`() {
        val totals = calculator.expensesByCategory(expenses)

        assertEquals(2, totals.size)
        assertTrue(totals.first().total >= totals.last().total)
        assertEquals(Money.ofPesos(1_800.0), totals.first { it.categoryName == "Gasolina" }.total)
    }

    @Test
    fun `avisa cuando los gastos superan a los ingresos de una quincena`() {
        val overspent = listOf(TestFixtures.expense(day = 20, pesos = 10_500.0))
        val onlySecondIncome = listOf(TestFixtures.income(day = 30, pesos = 9_722.0))

        val summary = calculator.monthSummary(january, onlySecondIncome, overspent)
        val alerts = calculator.alerts(summary, Money.ofPesos(500.0))

        assertTrue(alerts.any { it.severity == BudgetAlert.Severity.WARNING })
        assertEquals(Money.ofPesos(-778.0), summary.second.remaining)
    }

    @Test
    fun `avisa cuando el sobrante queda por debajo del umbral`() {
        val tight = listOf(TestFixtures.expense(day = 5, pesos = 13_400.0))
        val firstIncome = listOf(TestFixtures.income(day = 15, pesos = 13_600.0))

        val summary = calculator.monthSummary(january, firstIncome, tight)
        val alerts = calculator.alerts(summary, Money.ofPesos(500.0))

        assertTrue(alerts.any { it.severity == BudgetAlert.Severity.CAUTION })
    }

    @Test
    fun `sin movimientos todos los totales son cero`() {
        val summary = calculator.monthSummary(january, emptyList(), emptyList())

        assertEquals(Money.ZERO, summary.income)
        assertEquals(Money.ZERO, summary.expenses)
        assertEquals(Money.ZERO, summary.remaining)
        assertTrue(calculator.alerts(summary, Money.ofPesos(500.0)).isEmpty())
    }

    @Test
    fun `el desglose diario cubre todos los dias del mes`() {
        val byDay = calculator.dailyBreakdown(january, incomes, expenses)

        assertEquals(31, byDay.size)
        assertEquals(Money.ofPesos(900.0), byDay.getValue(january.atDay(10)).totalExpenses)
        assertTrue(byDay.getValue(january.atDay(1)).expenses.isEmpty())
    }
}
