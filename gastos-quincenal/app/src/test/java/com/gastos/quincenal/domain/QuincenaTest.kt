package com.gastos.quincenal.domain

import com.gastos.quincenal.domain.model.Quincena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class QuincenaTest {

    @Test
    fun `dias 1 al 15 pertenecen a la primera quincena`() {
        (1..15).forEach { day ->
            assertEquals(Quincena.PRIMERA, Quincena.of(LocalDate.of(2026, 1, day)))
        }
    }

    @Test
    fun `dias 16 en adelante pertenecen a la segunda quincena`() {
        (16..31).forEach { day ->
            assertEquals(Quincena.SEGUNDA, Quincena.of(LocalDate.of(2026, 1, day)))
        }
    }

    @Test
    fun `la segunda quincena de febrero termina el 28 en ano comun`() {
        val february = YearMonth.of(2026, 2)
        assertEquals(LocalDate.of(2026, 2, 28), Quincena.SEGUNDA.endDate(february))
        assertEquals("16 - 28", Quincena.SEGUNDA.dayRangeLabel(february))
    }

    @Test
    fun `la segunda quincena de febrero termina el 29 en ano bisiesto`() {
        val february = YearMonth.of(2024, 2)
        assertEquals(LocalDate.of(2024, 2, 29), Quincena.SEGUNDA.endDate(february))
        assertEquals("16 - 29", Quincena.SEGUNDA.dayRangeLabel(february))
    }

    @Test
    fun `la segunda quincena de abril termina el 30`() {
        val april = YearMonth.of(2026, 4)
        assertEquals(LocalDate.of(2026, 4, 30), Quincena.SEGUNDA.endDate(april))
    }

    @Test
    fun `la primera quincena siempre va del 1 al 15`() {
        listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 1), YearMonth.of(2024, 2)).forEach { month ->
            assertEquals(month.atDay(1), Quincena.PRIMERA.startDate(month))
            assertEquals(month.atDay(15), Quincena.PRIMERA.endDate(month))
        }
    }

    @Test
    fun `contains distingue correctamente las dos quincenas`() {
        assertTrue(Quincena.PRIMERA.contains(LocalDate.of(2026, 3, 15)))
        assertFalse(Quincena.PRIMERA.contains(LocalDate.of(2026, 3, 16)))
        assertTrue(Quincena.SEGUNDA.contains(LocalDate.of(2026, 3, 31)))
    }
}
