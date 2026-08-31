package com.qareport.generator.domain.model

/**
 * Todos los enums del dominio exponen:
 *  - [key]   : valor estable persistido en Room / exportaciones (NUNCA usar name() para persistir,
 *              porque renombrar una constante rompería la base de datos existente).
 *  - [label] : texto mostrado al usuario.
 *
 * Cada companion expone un `from(...)` tolerante a fallos: si el valor almacenado es nulo,
 * desconocido o corrupto se devuelve un valor por defecto en lugar de lanzar una excepción.
 * Esto es parte del requisito de estabilidad: la app no debe cerrarse por datos inesperados.
 */

enum class TestStatus(val key: String, val label: String) {
    NOT_TESTED("NOT_TESTED", "NOT TESTED"),
    PASS("PASS", "PASS"),
    FAIL("FAIL", "FAIL"),
    BLOCKED("BLOCKED", "BLOCKED"),
    SKIPPED("SKIPPED", "SKIPPED");

    /** Un caso se considera ejecutado cuando ya tiene un resultado distinto de NOT_TESTED. */
    val isExecuted: Boolean get() = this != NOT_TESTED

    companion object {
        val DEFAULT: TestStatus = NOT_TESTED

        fun from(value: String?): TestStatus =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class Priority(val key: String, val label: String, val weight: Int) {
    LOW("LOW", "LOW", 1),
    MEDIUM("MEDIUM", "MEDIUM", 2),
    HIGH("HIGH", "HIGH", 3),
    CRITICAL("CRITICAL", "CRITICAL", 4);

    companion object {
        val DEFAULT: Priority = MEDIUM

        fun from(value: String?): Priority =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class Severity(val key: String, val label: String, val weight: Int) {
    MINOR("MINOR", "MINOR", 1),
    MAJOR("MAJOR", "MAJOR", 2),
    CRITICAL("CRITICAL", "CRITICAL", 3),
    BLOCKER("BLOCKER", "BLOCKER", 4);

    companion object {
        val DEFAULT: Severity = MAJOR

        /** Devuelve null cuando no hay severidad asignada (por ejemplo un caso PASS). */
        fun fromOrNull(value: String?): Severity? =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) }

        fun from(value: String?): Severity = fromOrNull(value) ?: DEFAULT
    }
}

enum class BugStatus(val key: String, val label: String) {
    OPEN("OPEN", "OPEN"),
    IN_PROGRESS("IN_PROGRESS", "IN PROGRESS"),
    FIXED("FIXED", "FIXED"),
    RETEST("RETEST", "RETEST"),
    CLOSED("CLOSED", "CLOSED"),
    REJECTED("REJECTED", "REJECTED");

    /** Un defecto sigue pendiente mientras no esté cerrado o rechazado. */
    val isOpenIssue: Boolean get() = this != CLOSED && this != REJECTED

    companion object {
        val DEFAULT: BugStatus = OPEN

        fun from(value: String?): BugStatus =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class EvidenceType(val key: String, val label: String) {
    PHOTO("PHOTO", "Fotografía"),
    SCREENSHOT("SCREENSHOT", "Captura de pantalla"),
    TEXT("TEXT", "Archivo TXT"),
    LOG("LOG", "Log"),
    PDF("PDF", "PDF"),
    OTHER("OTHER", "Otro archivo");

    val isImage: Boolean get() = this == PHOTO || this == SCREENSHOT

    companion object {
        val DEFAULT: EvidenceType = OTHER

        fun from(value: String?): EvidenceType =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT

        /**
         * Deduce el tipo a partir del mime type y del nombre del archivo.
         * Se usa al adjuntar evidencias mediante el Storage Access Framework.
         */
        fun fromMimeType(mimeType: String?, fileName: String?): EvidenceType {
            val mime = mimeType?.lowercase()?.trim().orEmpty()
            val name = fileName?.lowercase()?.trim().orEmpty()
            return when {
                mime.startsWith("image/") -> SCREENSHOT
                mime == "application/pdf" || name.endsWith(".pdf") -> PDF
                name.endsWith(".log") -> LOG
                mime.startsWith("text/") || name.endsWith(".txt") -> TEXT
                else -> OTHER
            }
        }
    }
}

enum class ReportFormat(val key: String, val label: String, val extension: String, val mimeType: String) {
    PDF("PDF", "PDF", "pdf", "application/pdf"),
    CSV("CSV", "CSV", "csv", "text/csv"),
    JSON("JSON", "JSON", "json", "application/json"),
    TXT("TXT", "TXT", "txt", "text/plain");

    companion object {
        val DEFAULT: ReportFormat = PDF

        fun from(value: String?): ReportFormat =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("SYSTEM", "Sistema"),
    LIGHT("LIGHT", "Claro"),
    DARK("DARK", "Oscuro");

    companion object {
        val DEFAULT: ThemeMode = SYSTEM

        fun from(value: String?): ThemeMode =
            entries.firstOrNull { it.key.equals(value?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
