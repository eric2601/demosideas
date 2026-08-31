package com.gastos.quincenal.domain.recurrence

import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.utils.DateUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Expande una [RecurrenceRule] a las fechas concretas que le corresponden dentro
 * de un mes (o de un rango). Es codigo puro y sin dependencias de Android para
 * poder cubrirlo con pruebas unitarias de JVM.
 */
object RecurrenceEngine {

    /**
     * Fechas generadas por la regla dentro del mes indicado, en orden ascendente.
     *
     * Devuelve lista vacia si la regla esta inactiva, si el mes es anterior al
     * inicio o posterior al fin.
     */
    fun occurrencesInMonth(rule: RecurrenceRule, yearMonth: YearMonth): List<LocalDate> {
        if (!rule.active) return emptyList()

        val monthStart = yearMonth.atDay(1)
        val monthEnd = yearMonth.atEndOfMonth()

        // Fuera de la ventana de vigencia de la regla.
        if (monthEnd.isBefore(rule.startDate)) return emptyList()
        rule.endDate?.let { if (monthStart.isAfter(it)) return emptyList() }

        val candidates = when (rule.frequency) {
            Frequency.ONCE -> onceOccurrences(rule, yearMonth)
            Frequency.WEEKLY, Frequency.BIWEEKLY -> weeklyOccurrences(rule, yearMonth)
            Frequency.MONTHLY -> monthlyOccurrences(rule, yearMonth)
            Frequency.YEARLY -> yearlyOccurrences(rule, yearMonth)
        }

        return candidates
            .filter { !it.isBefore(rule.startDate) }
            .filter { date -> rule.endDate?.let { !date.isAfter(it) } ?: true }
            .distinct()
            .sorted()
    }

    /** Fechas generadas por la regla dentro de un rango cerrado [from, to]. */
    fun occurrencesInRange(rule: RecurrenceRule, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (from.isAfter(to)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var month = YearMonth.of(from.year, from.month)
        val lastMonth = YearMonth.of(to.year, to.month)
        while (!month.isAfter(lastMonth)) {
            result += occurrencesInMonth(rule, month).filter { !it.isBefore(from) && !it.isAfter(to) }
            month = month.plusMonths(1)
        }
        return result
    }

    /** Indica si la regla produce al menos una fecha en el mes. */
    fun appliesTo(rule: RecurrenceRule, yearMonth: YearMonth): Boolean =
        occurrencesInMonth(rule, yearMonth).isNotEmpty()

    // ---------------------------------------------------------------------
    // Estrategias por frecuencia
    // ---------------------------------------------------------------------

    private fun onceOccurrences(rule: RecurrenceRule, yearMonth: YearMonth): List<LocalDate> =
        if (DateUtils.yearMonthOf(rule.startDate) == yearMonth) listOf(rule.startDate) else emptyList()

    /**
     * Semanal / quincenal: se toman todos los dias del mes que caen en el dia de
     * la semana pedido y, cuando el intervalo es mayor que 1, se conservan solo
     * los que estan a un multiplo exacto de semanas de la primera ocurrencia.
     */
    private fun weeklyOccurrences(rule: RecurrenceRule, yearMonth: YearMonth): List<LocalDate> {
        val targetDay = rule.dayOfWeek ?: rule.startDate.dayOfWeek
        val anchor = firstDateOnOrAfter(rule.startDate, targetDay)
        val step = rule.weekStep

        return DateUtils.daysOfMonth(yearMonth)
            .filter { it.dayOfWeek == targetDay && !it.isBefore(anchor) }
            .filter { date ->
                val weeks = ChronoUnit.WEEKS.between(anchor, date)
                weeks >= 0 && weeks % step == 0L
            }
    }

    /**
     * Mensual: un dia fijo del mes, recortado a la longitud real del mes
     * (el dia 31 cae el 28 o 29 en febrero) y respetando el intervalo en meses.
     */
    private fun monthlyOccurrences(rule: RecurrenceRule, yearMonth: YearMonth): List<LocalDate> {
        val startMonth = YearMonth.of(rule.startDate.year, rule.startDate.month)
        val monthsBetween = ChronoUnit.MONTHS.between(startMonth, yearMonth)
        if (monthsBetween < 0 || monthsBetween % rule.safeInterval != 0L) return emptyList()

        val day = rule.dayOfMonth ?: rule.startDate.dayOfMonth
        return listOf(DateUtils.clampDayOfMonth(yearMonth, day))
    }

    /** Anual: mismo mes y dia que la fecha de inicio, respetando el intervalo en anos. */
    private fun yearlyOccurrences(rule: RecurrenceRule, yearMonth: YearMonth): List<LocalDate> {
        if (yearMonth.monthValue != rule.startDate.monthValue) return emptyList()
        val yearsBetween = (yearMonth.year - rule.startDate.year).toLong()
        if (yearsBetween < 0 || yearsBetween % rule.safeInterval != 0L) return emptyList()

        val day = rule.dayOfMonth ?: rule.startDate.dayOfMonth
        return listOf(DateUtils.clampDayOfMonth(yearMonth, day))
    }

    /** Primera fecha igual o posterior a [from] que cae en [dayOfWeek]. */
    private fun firstDateOnOrAfter(from: LocalDate, dayOfWeek: java.time.DayOfWeek): LocalDate {
        val diff = (dayOfWeek.value - from.dayOfWeek.value + 7) % 7
        return from.plusDays(diff.toLong())
    }
}
