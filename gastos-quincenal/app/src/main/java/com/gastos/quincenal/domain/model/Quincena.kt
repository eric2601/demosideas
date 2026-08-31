package com.gastos.quincenal.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Las dos quincenas en que se divide cualquier mes.
 *
 * PRIMERA: dia 1 al 15.
 * SEGUNDA: dia 16 al ultimo dia real del mes (28, 29, 30 o 31 segun corresponda).
 */
enum class Quincena {
    PRIMERA,
    SEGUNDA;

    val displayName: String
        get() = when (this) {
            PRIMERA -> "Primera quincena"
            SEGUNDA -> "Segunda quincena"
        }

    val shortName: String
        get() = when (this) {
            PRIMERA -> "1a quincena"
            SEGUNDA -> "2a quincena"
        }

    /** Primer dia de la quincena dentro del mes indicado. */
    fun startDate(yearMonth: YearMonth): LocalDate = when (this) {
        PRIMERA -> yearMonth.atDay(FIRST_DAY)
        SEGUNDA -> yearMonth.atDay(SECOND_START_DAY)
    }

    /** Ultimo dia de la quincena dentro del mes indicado (respeta meses cortos y bisiestos). */
    fun endDate(yearMonth: YearMonth): LocalDate = when (this) {
        PRIMERA -> yearMonth.atDay(minOf(FIRST_END_DAY, yearMonth.lengthOfMonth()))
        SEGUNDA -> yearMonth.atEndOfMonth()
    }

    /** Rango de dias de la quincena, por ejemplo "1 - 15" o "16 - 29". */
    fun dayRangeLabel(yearMonth: YearMonth): String =
        "${startDate(yearMonth).dayOfMonth} - ${endDate(yearMonth).dayOfMonth}"

    fun contains(date: LocalDate): Boolean = of(date) == this

    companion object {
        const val FIRST_DAY = 1
        const val FIRST_END_DAY = 15
        const val SECOND_START_DAY = 16

        /**
         * Regla unica de asignacion: el dia del mes determina la quincena.
         * El usuario nunca elige la quincena a mano cuando ya existe una fecha valida.
         */
        fun of(date: LocalDate): Quincena =
            if (date.dayOfMonth <= FIRST_END_DAY) PRIMERA else SEGUNDA
    }
}
