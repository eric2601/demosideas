package com.gastos.quincenal.domain

import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.recurrence.RecurrenceEngine
import com.gastos.quincenal.domain.recurrence.RecurrenceRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class RecurrenceEngineTest {

    @Test
    fun `mensual aparece en todos los meses siguientes`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 1),
            dayOfMonth = 1
        )

        assertEquals(
            listOf(LocalDate.of(2026, 1, 1)),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 1))
        )
        assertEquals(
            listOf(LocalDate.of(2026, 2, 1)),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 2))
        )
        assertEquals(
            listOf(LocalDate.of(2026, 12, 1)),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 12))
        )
    }

    @Test
    fun `mensual no aparece antes de su fecha de inicio`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2026, 3, 10),
            dayOfMonth = 10
        )
        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 2)).isEmpty())
    }

    @Test
    fun `mensual el dia 31 se recorta a la longitud real del mes`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 31),
            dayOfMonth = 31
        )

        assertEquals(
            listOf(LocalDate.of(2026, 2, 28)),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 2))
        )
        assertEquals(
            listOf(LocalDate.of(2024, 2, 29)),
            RecurrenceEngine.occurrencesInMonth(rule.copy(startDate = LocalDate.of(2024, 1, 31)), YearMonth.of(2024, 2))
        )
        assertEquals(
            listOf(LocalDate.of(2026, 4, 30)),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 4))
        )
    }

    @Test
    fun `semanal coloca el gasto en cada viernes del mes`() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            startDate = LocalDate.of(2026, 1, 1),
            dayOfWeek = DayOfWeek.FRIDAY
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 9),
                LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 1, 23),
                LocalDate.of(2026, 1, 30)
            ),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 1))
        )
    }

    @Test
    fun `cada dos semanas salta una semana completa`() {
        val rule = RecurrenceRule(
            frequency = Frequency.BIWEEKLY,
            startDate = LocalDate.of(2026, 1, 2),
            dayOfWeek = DayOfWeek.FRIDAY
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 16),
                LocalDate.of(2026, 1, 30)
            ),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 1))
        )
    }

    @Test
    fun `cada dos semanas mantiene el ritmo al cruzar de mes`() {
        val rule = RecurrenceRule(
            frequency = Frequency.BIWEEKLY,
            startDate = LocalDate.of(2026, 1, 2),
            dayOfWeek = DayOfWeek.FRIDAY
        )

        assertEquals(
            listOf(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 2, 27)),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 2))
        )
    }

    @Test
    fun `solo una vez aparece unicamente en su propio mes`() {
        val rule = RecurrenceRule(
            frequency = Frequency.ONCE,
            startDate = LocalDate.of(2026, 1, 20)
        )

        assertEquals(
            listOf(LocalDate.of(2026, 1, 20)),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 1))
        )
        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 2)).isEmpty())
    }

    @Test
    fun `la fecha limite corta la serie`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 5),
            endDate = LocalDate.of(2026, 12, 31),
            dayOfMonth = 5
        )

        assertEquals(1, RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 12)).size)
        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2027, 1)).isEmpty())
    }

    @Test
    fun `una regla inactiva no genera nada`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 5),
            dayOfMonth = 5,
            active = false
        )
        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 5)).isEmpty())
    }

    @Test
    fun `anual solo aparece en el mismo mes de cada ano`() {
        val rule = RecurrenceRule(
            frequency = Frequency.YEARLY,
            startDate = LocalDate.of(2026, 4, 15),
            dayOfMonth = 15
        )

        assertEquals(
            listOf(LocalDate.of(2027, 4, 15)),
            RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2027, 4))
        )
        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2027, 5)).isEmpty())
    }

    @Test
    fun `mensual con intervalo de tres meses respeta el salto`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            interval = 3,
            startDate = LocalDate.of(2026, 1, 10),
            dayOfMonth = 10
        )

        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 1)).isNotEmpty())
        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 2)).isEmpty())
        assertTrue(RecurrenceEngine.occurrencesInMonth(rule, YearMonth.of(2026, 4)).isNotEmpty())
    }

    @Test
    fun `occurrencesInRange recorre varios meses`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2026, 1, 15),
            dayOfMonth = 15
        )

        val dates = RecurrenceEngine.occurrencesInRange(
            rule,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31)
        )
        assertEquals(3, dates.size)
        assertEquals(LocalDate.of(2026, 3, 15), dates.last())
    }
}
