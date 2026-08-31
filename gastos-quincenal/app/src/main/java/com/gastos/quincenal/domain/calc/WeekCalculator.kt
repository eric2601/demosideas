package com.gastos.quincenal.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * Divide un mes en semanas naturales de domingo a sabado, recortadas a los dias
 * que realmente pertenecen al mes. Coincide con las filas del calendario que se
 * muestra en pantalla, para que "Semana 2" signifique lo mismo en ambos lugares.
 */
object WeekCalculator {

    /** Rango cerrado de dias de una semana del mes. */
    data class WeekRange(val index: Int, val start: LocalDate, val end: LocalDate) {
        fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    }

    /**
     * Semanas del mes. La primera empieza el dia 1 (aunque no sea domingo) y la
     * ultima termina el ultimo dia del mes.
     */
    fun weeksOf(yearMonth: YearMonth): List<WeekRange> {
        val firstDay = yearMonth.atDay(1)
        val lastDay = yearMonth.atEndOfMonth()

        val weeks = mutableListOf<WeekRange>()
        var cursor = firstDay
        var index = 1
        while (!cursor.isAfter(lastDay)) {
            val endOfWeek = cursor.plusDays(daysUntilSaturday(cursor).toLong())
            val end = if (endOfWeek.isAfter(lastDay)) lastDay else endOfWeek
            weeks += WeekRange(index, cursor, end)
            cursor = end.plusDays(1)
            index++
        }
        return weeks
    }

    /** Semana del mes a la que pertenece la fecha, o `null` si no es de ese mes. */
    fun weekOf(date: LocalDate): WeekRange? {
        val yearMonth = YearMonth.of(date.year, date.month)
        return weeksOf(yearMonth).firstOrNull { it.contains(date) }
    }

    /** Dias que faltan desde [date] hasta el sabado de su misma semana. */
    private fun daysUntilSaturday(date: LocalDate): Int =
        SATURDAY_INDEX - sundayBasedIndex(date.dayOfWeek)

    /** Domingo = 0, lunes = 1, ..., sabado = 6. */
    fun sundayBasedIndex(dayOfWeek: DayOfWeek): Int = dayOfWeek.value % 7

    private const val SATURDAY_INDEX = 6
}
