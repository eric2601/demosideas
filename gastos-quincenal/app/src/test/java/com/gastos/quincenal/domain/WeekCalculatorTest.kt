package com.gastos.quincenal.domain

import com.gastos.quincenal.domain.calc.WeekCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class WeekCalculatorTest {

    @Test
    fun `enero 2026 se divide en cinco semanas de domingo a sabado`() {
        val weeks = WeekCalculator.weeksOf(YearMonth.of(2026, 1))

        assertEquals(5, weeks.size)
        // El 1 de enero de 2026 es jueves: la primera semana va del 1 al 3.
        assertEquals(LocalDate.of(2026, 1, 1), weeks.first().start)
        assertEquals(LocalDate.of(2026, 1, 3), weeks.first().end)
        assertEquals(LocalDate.of(2026, 1, 4), weeks[1].start)
        assertEquals(LocalDate.of(2026, 1, 10), weeks[1].end)
        assertEquals(LocalDate.of(2026, 1, 31), weeks.last().end)
    }

    @Test
    fun `las semanas cubren el mes completo sin huecos ni solapes`() {
        listOf(
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 2),
            YearMonth.of(2024, 2),
            YearMonth.of(2026, 11)
        ).forEach { month ->
            val weeks = WeekCalculator.weeksOf(month)
            val covered = weeks.flatMap { week ->
                generateSequence(week.start) { day ->
                    day.plusDays(1).takeIf { !it.isAfter(week.end) }
                }.toList()
            }
            assertEquals(month.lengthOfMonth(), covered.size)
            assertEquals(covered.size, covered.distinct().size)
            assertEquals(month.atDay(1), covered.first())
            assertEquals(month.atEndOfMonth(), covered.last())
        }
    }

    @Test
    fun `weekOf encuentra la semana que contiene la fecha`() {
        val week = WeekCalculator.weekOf(LocalDate.of(2026, 1, 7))

        assertTrue(week != null)
        assertEquals(2, week!!.index)
        assertTrue(week.contains(LocalDate.of(2026, 1, 4)))
        assertTrue(week.contains(LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun `el domingo es el primer dia de la semana`() {
        assertEquals(0, WeekCalculator.sundayBasedIndex(DayOfWeek.SUNDAY))
        assertEquals(1, WeekCalculator.sundayBasedIndex(DayOfWeek.MONDAY))
        assertEquals(6, WeekCalculator.sundayBasedIndex(DayOfWeek.SATURDAY))
    }
}
