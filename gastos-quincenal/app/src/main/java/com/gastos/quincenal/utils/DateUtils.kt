package com.gastos.quincenal.utils

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Utilidades de fecha en espanol de Mexico.
 *
 * Se centralizan aqui para que la UI no construya formateadores ni haga aritmetica
 * de calendario por su cuenta.
 */
object DateUtils {

    val LOCALE: Locale = Locale.forLanguageTag("es-MX")

    private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE)
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE

    private val MONTH_NAMES = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    /** "Enero" */
    fun monthName(yearMonth: YearMonth): String = MONTH_NAMES[yearMonth.monthValue - 1]

    /** "ENERO 2026" */
    fun monthTitle(yearMonth: YearMonth): String =
        "${monthName(yearMonth).uppercase(LOCALE)} ${yearMonth.year}"

    /** "Enero 2026" */
    fun monthTitleCapitalized(yearMonth: YearMonth): String =
        "${monthName(yearMonth)} ${yearMonth.year}"

    /** "10/01/2026" */
    fun formatShort(date: LocalDate): String = date.format(DAY_MONTH_YEAR)

    /** "10 de enero de 2026" */
    fun formatLong(date: LocalDate): String =
        "${date.dayOfMonth} de ${MONTH_NAMES[date.monthValue - 1].lowercase(LOCALE)} de ${date.year}"

    /** "10 de enero" */
    fun formatDayMonth(date: LocalDate): String =
        "${date.dayOfMonth} de ${MONTH_NAMES[date.monthValue - 1].lowercase(LOCALE)}"

    fun toIso(date: LocalDate): String = date.format(ISO)

    fun parseIsoOrNull(value: String?): LocalDate? =
        if (value.isNullOrBlank()) null else runCatching { LocalDate.parse(value, ISO) }.getOrNull()

    fun yearMonthToIso(yearMonth: YearMonth): String =
        "%04d-%02d".format(yearMonth.year, yearMonth.monthValue)

    fun parseYearMonthOrNull(value: String?): YearMonth? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val parts = value.split("-")
            YearMonth.of(parts[0].toInt(), parts[1].toInt())
        }.getOrNull()
    }

    fun yearMonthOf(date: LocalDate): YearMonth = YearMonth.of(date.year, date.month)

    /**
     * Ajusta un dia del mes al mes indicado sin desbordarse.
     * Por ejemplo, el dia 31 en febrero de 2026 se convierte en el 28.
     */
    fun clampDayOfMonth(yearMonth: YearMonth, dayOfMonth: Int): LocalDate {
        val safeDay = dayOfMonth.coerceIn(1, yearMonth.lengthOfMonth())
        return yearMonth.atDay(safeDay)
    }

    /** Todos los dias del mes, del 1 al ultimo dia real. */
    fun daysOfMonth(yearMonth: YearMonth): List<LocalDate> =
        (1..yearMonth.lengthOfMonth()).map { yearMonth.atDay(it) }
}
