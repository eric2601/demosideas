package com.qareport.generator.domain.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Formateo de fechas basado en [SimpleDateFormat] (disponible en cualquier nivel de API
 * y en la JVM), por lo que no se requiere desugaring de java.time.
 *
 * Las instancias de SimpleDateFormat NO son thread-safe: se crean por llamada, que es
 * suficiente para el volumen de formateos de esta aplicación.
 */
object DateFormats {

    private const val PATTERN_DATE = "dd/MM/yyyy"
    private const val PATTERN_SHORT_DATE = "dd/MM"
    private const val PATTERN_DATE_TIME = "dd/MM/yyyy HH:mm"
    private const val PATTERN_TIMESTAMP_FILE = "yyyyMMdd_HHmmss"
    private const val PATTERN_ISO = "yyyy-MM-dd'T'HH:mm:ss"

    private fun format(pattern: String, millis: Long): String = try {
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
    } catch (error: IllegalArgumentException) {
        // Nunca propagar un fallo de formateo hacia la UI o el PDF.
        ""
    }

    fun formatDate(millis: Long?): String = if (millis == null || millis <= 0L) "-" else format(PATTERN_DATE, millis)

    fun formatShortDate(millis: Long): String = format(PATTERN_SHORT_DATE, millis)

    fun formatDateTime(millis: Long?): String =
        if (millis == null || millis <= 0L) "-" else format(PATTERN_DATE_TIME, millis)

    fun formatIso(millis: Long?): String = if (millis == null || millis <= 0L) "" else format(PATTERN_ISO, millis)

    /** Sufijo seguro para nombres de archivo, p. ej. 20260831_142530. */
    fun fileTimestamp(millis: Long = System.currentTimeMillis()): String = format(PATTERN_TIMESTAMP_FILE, millis)

    /**
     * Convierte una fecha "dd/MM/yyyy" a epoch millis, o null si el texto no es una fecha válida.
     * Se usa para validar la entrada manual del usuario sin lanzar excepciones.
     */
    fun parseDateOrNull(text: String?): Long? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            val formatter = SimpleDateFormat(PATTERN_DATE, Locale.getDefault())
            formatter.isLenient = false
            formatter.parse(trimmed)?.time
        } catch (error: java.text.ParseException) {
            null
        }
    }

    /** Normaliza un instante al inicio del día local. */
    fun startOfDay(millis: Long): Long {
        val calendar = Calendar.getInstance(Locale.getDefault())
        calendar.timeInMillis = millis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
