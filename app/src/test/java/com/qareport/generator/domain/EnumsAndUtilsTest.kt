package com.qareport.generator.domain

import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.EvidenceType
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.model.ThemeMode
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.util.TextUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnumsAndUtilsTest {

    @Test
    fun `los enums caen en su valor por defecto ante datos corruptos`() {
        assertEquals(TestStatus.NOT_TESTED, TestStatus.from(null))
        assertEquals(TestStatus.NOT_TESTED, TestStatus.from("basura"))
        assertEquals(TestStatus.PASS, TestStatus.from("pass"))
        assertEquals(TestStatus.PASS, TestStatus.from("  PASS  "))

        assertEquals(Priority.MEDIUM, Priority.from(""))
        assertEquals(Priority.CRITICAL, Priority.from("critical"))

        assertEquals(BugStatus.OPEN, BugStatus.from("???"))
        assertEquals(BugStatus.IN_PROGRESS, BugStatus.from("IN_PROGRESS"))

        assertEquals(ThemeMode.SYSTEM, ThemeMode.from(null))
        assertEquals(ThemeMode.DARK, ThemeMode.from("DARK"))
    }

    @Test
    fun `la severidad ausente se representa como null`() {
        assertNull(Severity.fromOrNull(null))
        assertNull(Severity.fromOrNull("desconocida"))
        assertEquals(Severity.BLOCKER, Severity.fromOrNull("BLOCKER"))
        assertEquals(Severity.MAJOR, Severity.from("desconocida"))
    }

    @Test
    fun `isExecuted distingue los casos ya ejecutados`() {
        assertFalse(TestStatus.NOT_TESTED.isExecuted)
        assertTrue(TestStatus.PASS.isExecuted)
        assertTrue(TestStatus.BLOCKED.isExecuted)
        assertTrue(TestStatus.SKIPPED.isExecuted)
    }

    @Test
    fun `isOpenIssue distingue los defectos pendientes`() {
        assertTrue(BugStatus.OPEN.isOpenIssue)
        assertTrue(BugStatus.RETEST.isOpenIssue)
        assertFalse(BugStatus.CLOSED.isOpenIssue)
        assertFalse(BugStatus.REJECTED.isOpenIssue)
    }

    @Test
    fun `deduce el tipo de evidencia desde el mime type`() {
        assertEquals(EvidenceType.SCREENSHOT, EvidenceType.fromMimeType("image/png", "a.png"))
        assertEquals(EvidenceType.PDF, EvidenceType.fromMimeType("application/pdf", "a.pdf"))
        assertEquals(EvidenceType.PDF, EvidenceType.fromMimeType(null, "reporte.pdf"))
        assertEquals(EvidenceType.LOG, EvidenceType.fromMimeType("text/plain", "logcat.log"))
        assertEquals(EvidenceType.TEXT, EvidenceType.fromMimeType("text/plain", "notas.txt"))
        assertEquals(EvidenceType.OTHER, EvidenceType.fromMimeType("application/zip", "a.zip"))
    }

    @Test
    fun `los nombres de archivo se sanean`() {
        assertEquals("Terminal_P630", TextUtils.toSafeFileName("Terminal P630"))
        assertEquals("Instalacion", TextUtils.toSafeFileName("Instalación"))
        assertEquals("ab", TextUtils.toSafeFileName("a/b"))
        assertEquals("reporte", TextUtils.toSafeFileName("///", fallback = "reporte"))
        assertTrue(TextUtils.toSafeFileName("x".repeat(200)).length <= 80)
    }

    @Test
    fun `ellipsize respeta el limite`() {
        assertEquals("hola", TextUtils.ellipsize("hola", 10))
        assertEquals(5, TextUtils.ellipsize("texto muy largo", 5).length)
        assertEquals("", TextUtils.ellipsize("hola", 0))
    }

    @Test
    fun `splitSteps limpia lineas vacias`() {
        val steps = TextUtils.splitSteps("1. Uno\n\n  2. Dos  \n\n")

        assertEquals(listOf("1. Uno", "2. Dos"), steps)
    }

    @Test
    fun `formatBytes usa unidades legibles`() {
        assertEquals("512 B", TextUtils.formatBytes(512))
        assertEquals("1.0 KB", TextUtils.formatBytes(1024))
        assertEquals("1.0 MB", TextUtils.formatBytes(1024L * 1024L))
        assertEquals("-", TextUtils.formatBytes(-1))
    }

    @Test
    fun `las fechas nulas o cero se muestran como guion`() {
        assertEquals("-", DateFormats.formatDate(null))
        assertEquals("-", DateFormats.formatDate(0L))
        assertEquals("-", DateFormats.formatDateTime(null))
        assertEquals("", DateFormats.formatIso(null))
    }

    @Test
    fun `parseDateOrNull acepta solo fechas reales`() {
        assertNotNull(DateFormats.parseDateOrNull("31/08/2026"))
        assertNull(DateFormats.parseDateOrNull("32/08/2026"))
        assertNull(DateFormats.parseDateOrNull(""))
        assertNull(DateFormats.parseDateOrNull(null))
    }

    @Test
    fun `una fecha formateada se puede volver a parsear`() {
        val millis = DateFormats.parseDateOrNull("15/03/2026")

        assertNotNull(millis)
        assertEquals("15/03/2026", DateFormats.formatDate(millis))
    }

    @Test
    fun `startOfDay normaliza al inicio del dia`() {
        val millis = DateFormats.parseDateOrNull("15/03/2026")!!

        assertEquals(millis, DateFormats.startOfDay(millis + 3_600_000L * 13))
    }

    @Test
    fun `el timestamp de archivo tiene el formato esperado`() {
        val stamp = DateFormats.fileTimestamp(DateFormats.parseDateOrNull("31/08/2026")!!)

        assertTrue(stamp.matches(Regex("\\d{8}_\\d{6}")))
        assertTrue(stamp.startsWith("20260831"))
    }
}
