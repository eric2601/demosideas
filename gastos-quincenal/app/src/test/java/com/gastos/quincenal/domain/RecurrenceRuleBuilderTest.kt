package com.gastos.quincenal.domain

import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.RecurrenceEnd
import com.gastos.quincenal.domain.recurrence.RecurrenceEngine
import com.gastos.quincenal.domain.usecase.SaveExpenseUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Comprueba la traduccion del formulario a regla de recurrencia, que es donde se
 * decide si un gasto reaparece o no en los meses siguientes.
 */
class RecurrenceRuleBuilderTest {

    private val january10 = LocalDate.of(2026, 1, 10)

    @Test
    fun `solo este mes no genera regla y no reaparece en febrero`() {
        val rule = SaveExpenseUseCase.buildRule(
            frequency = Frequency.ONCE,
            recurrenceEnd = RecurrenceEnd.ONLY_THIS_MONTH,
            date = january10,
            endDate = null,
            dayOfWeek = null,
            interval = 1
        )
        assertNull(rule)
    }

    @Test
    fun `repetir indefinidamente genera una regla mensual sin fecha final`() {
        val rule = SaveExpenseUseCase.buildRule(
            frequency = Frequency.MONTHLY,
            recurrenceEnd = RecurrenceEnd.FOREVER,
            date = january10,
            endDate = null,
            dayOfWeek = null,
            interval = 1
        )

        assertNotNull(rule)
        assertNull(rule!!.endDate)
        assertEquals(10, rule.dayOfMonth)
        assertTrue(RecurrenceEngine.appliesTo(rule, YearMonth.of(2030, 6)))
    }

    @Test
    fun `repetir hasta una fecha deja de aplicar despues del limite`() {
        val rule = SaveExpenseUseCase.buildRule(
            frequency = Frequency.MONTHLY,
            recurrenceEnd = RecurrenceEnd.UNTIL_DATE,
            date = january10,
            endDate = LocalDate.of(2026, 12, 31),
            dayOfWeek = null,
            interval = 1
        )

        assertNotNull(rule)
        assertTrue(RecurrenceEngine.appliesTo(rule!!, YearMonth.of(2026, 12)))
        assertTrue(!RecurrenceEngine.appliesTo(rule, YearMonth.of(2027, 1)))
    }

    @Test
    fun `semanal de un solo mes se limita al propio mes`() {
        val rule = SaveExpenseUseCase.buildRule(
            frequency = Frequency.WEEKLY,
            recurrenceEnd = RecurrenceEnd.ONLY_THIS_MONTH,
            date = LocalDate.of(2026, 1, 2),
            endDate = null,
            dayOfWeek = DayOfWeek.FRIDAY,
            interval = 1
        )

        assertNotNull(rule)
        assertEquals(LocalDate.of(2026, 1, 31), rule!!.endDate)
        assertEquals(5, RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 1)).size)
        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 2)).isEmpty())
    }

    @Test
    fun `semanal indefinido usa el dia de la semana elegido`() {
        val rule = SaveExpenseUseCase.buildRule(
            frequency = Frequency.WEEKLY,
            recurrenceEnd = RecurrenceEnd.FOREVER,
            date = january10,
            endDate = null,
            dayOfWeek = DayOfWeek.FRIDAY,
            interval = 1
        )

        assertNotNull(rule)
        assertEquals(DayOfWeek.FRIDAY, rule!!.dayOfWeek)
        RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 3)).forEach { date ->
            assertEquals(DayOfWeek.FRIDAY, date.dayOfWeek)
        }
    }
}
