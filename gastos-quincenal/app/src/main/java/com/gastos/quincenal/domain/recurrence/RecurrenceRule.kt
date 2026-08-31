package com.gastos.quincenal.domain.recurrence

import com.gastos.quincenal.domain.model.Frequency
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Regla de recurrencia en el dominio.
 *
 * La aplicacion NO duplica filas por cada mes: guarda esta regla una sola vez y
 * el [RecurrenceEngine] calcula las fechas del mes que se esta consultando.
 *
 * @param frequency que tan seguido se repite.
 * @param interval  cada cuantas unidades de la frecuencia (1 = cada semana/mes, 3 = cada 3 meses).
 * @param startDate primera fecha valida; nada se genera antes de ella.
 * @param endDate   ultima fecha valida inclusive; `null` significa indefinido.
 * @param dayOfMonth dia del mes para reglas mensuales/anuales (se recorta a meses cortos).
 * @param dayOfWeek dia de la semana para reglas semanales o quincenales.
 * @param active    permite desactivar la regla sin borrar el historico.
 */
data class RecurrenceRule(
    val id: Long = 0L,
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val dayOfMonth: Int? = null,
    val dayOfWeek: DayOfWeek? = null,
    val active: Boolean = true
) {
    /** Intervalo saneado: nunca menor que 1 aunque la base guarde un valor invalido. */
    val safeInterval: Int get() = interval.coerceAtLeast(1)

    /** Numero de semanas entre ocurrencias para reglas semanales. */
    val weekStep: Int
        get() = when (frequency) {
            Frequency.BIWEEKLY -> 2 * safeInterval
            else -> safeInterval
        }

    /** Descripcion legible para mostrar en las listas. */
    fun describe(): String {
        val base = when (frequency) {
            Frequency.ONCE -> "Solo una vez"
            Frequency.WEEKLY -> if (safeInterval == 1) "Cada semana" else "Cada $safeInterval semanas"
            Frequency.BIWEEKLY -> "Cada 2 semanas"
            Frequency.MONTHLY -> if (safeInterval == 1) "Cada mes" else "Cada $safeInterval meses"
            Frequency.YEARLY -> "Cada ano"
        }
        return if (endDate != null) "$base hasta ${endDate.dayOfMonth}/${endDate.monthValue}/${endDate.year}" else base
    }
}
