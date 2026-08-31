package com.qareport.generator.domain.util

import java.util.Locale

/** Utilidades de texto puras usadas por exportadores, nombres de archivo y el PDF. */
object TextUtils {

    private val UNSAFE_FILE_CHARS = Regex("[^A-Za-z0-9._-]")

    /**
     * Convierte un texto arbitrario en un nombre de archivo seguro para cualquier
     * sistema de archivos: sin espacios, sin acentos problemáticos y sin separadores.
     */
    fun toSafeFileName(raw: String, fallback: String = "reporte"): String {
        val normalized = java.text.Normalizer
            .normalize(raw.trim(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(' ', '_')
        val cleaned = UNSAFE_FILE_CHARS.replace(normalized, "")
            .trim('_', '.', '-')
        return cleaned.ifBlank { fallback }.take(80)
    }

    /** Recorta un texto largo añadiendo puntos suspensivos, sin romper por índices inválidos. */
    fun ellipsize(text: String, maxLength: Int): String {
        if (maxLength <= 0) return ""
        return if (text.length <= maxLength) text else text.take(maxLength - 1).trimEnd() + "…"
    }

    /** Divide un bloque de pasos en líneas numeradas limpias. */
    fun splitSteps(raw: String): List<String> =
        raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun orDash(text: String?): String = text?.trim().orEmpty().ifBlank { "-" }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "-"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun formatPercent(value: Double): String = String.format(Locale.US, "%.0f%%", value)
}
