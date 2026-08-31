package com.qareport.generator.domain

import com.qareport.generator.domain.export.CsvExporter
import com.qareport.generator.domain.export.Json
import com.qareport.generator.domain.export.JsonParser
import com.qareport.generator.domain.export.JsonValue
import com.qareport.generator.domain.export.ProjectJsonExporter
import com.qareport.generator.domain.export.TxtExporter
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Evidence
import com.qareport.generator.domain.model.EvidenceType
import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.ProjectBundle
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestExecution
import com.qareport.generator.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportersTest {

    private val bundle = ProjectBundle(
        project = Project(
            id = 1L,
            name = "Terminal P630 QA",
            client = "Empresa XYZ",
            product = "POS",
            version = "1.2.8",
            buildNumber = "128",
            deviceType = "Terminal",
            deviceModel = "Verifone P630",
            operatingSystem = "Android",
            androidVersion = "11",
            tester = "Eric",
            environment = "QA",
            startDate = 1_756_000_000_000L,
            endDate = 1_756_600_000_000L,
            description = "Pruebas funcionales de nueva versión.",
            notes = "Notas con \"comillas\", coma y\nsalto de línea",
            finalized = true,
            createdAt = 1_756_000_000_000L,
            updatedAt = 1_756_600_000_000L
        ),
        testCases = listOf(
            TestCase(
                id = 10L,
                projectId = 1L,
                code = "TC-001",
                title = "Instalación del APK",
                module = "Instalación",
                priority = Priority.HIGH,
                preconditions = "Dispositivo restaurado.",
                steps = "1. Conectar dispositivo.\n2. Ejecutar instalación.",
                expectedResult = "La aplicación debe instalarse correctamente.",
                actualResult = "Instalación completada.",
                status = TestStatus.PASS,
                orderIndex = 0,
                createdAt = 1L,
                updatedAt = 2L
            ),
            TestCase(
                id = 11L,
                projectId = 1L,
                code = "TC-004",
                title = "Transacción Contactless",
                module = "Transacciones",
                priority = Priority.CRITICAL,
                steps = "1. Iniciar transacción.\n2. Acercar tarjeta.",
                expectedResult = "La transacción debe aprobarse.",
                actualResult = "La aplicación deja de responder.",
                status = TestStatus.FAIL,
                severity = Severity.CRITICAL,
                comments = "Reproducible 3 de 3 veces",
                orderIndex = 1,
                createdAt = 1L,
                updatedAt = 2L
            )
        ),
        executions = listOf(
            TestExecution(
                id = 100L,
                projectId = 1L,
                testCaseId = 11L,
                status = TestStatus.FAIL,
                executedAt = 1_756_100_000_000L,
                executedBy = "Eric",
                problemDescription = "Terminal congelada",
                severity = Severity.CRITICAL,
                stepsToReproduce = "1. Iniciar transacción.",
                expectedResult = "Aprobada",
                actualResult = "Congelada"
            )
        ),
        bugs = listOf(
            Bug(
                id = 200L,
                projectId = 1L,
                testCaseId = 11L,
                code = "BUG-001",
                title = "Terminal congelada durante CTLS",
                description = "La terminal deja de responder.",
                severity = Severity.CRITICAL,
                priority = Priority.HIGH,
                stepsToReproduce = "1. Iniciar transacción.",
                expectedResult = "Continuar",
                actualResult = "Congelada",
                version = "1.2.8",
                device = "P630",
                status = BugStatus.OPEN,
                createdAt = 1_756_200_000_000L,
                updatedAt = 1_756_200_000_000L
            )
        ),
        evidences = listOf(
            Evidence(
                id = 300L,
                projectId = 1L,
                testCaseId = 11L,
                displayName = "pantalla_error.png",
                uri = "content://media/external/images/1",
                type = EvidenceType.SCREENSHOT,
                mimeType = "image/png",
                sizeBytes = 45_000L,
                createdAt = 1_756_300_000_000L
            )
        ),
        logs = listOf(
            LogEntry(
                id = 400L,
                projectId = 1L,
                title = "Logcat CTLS",
                content = "08:42:12 INFO Start transaction\n08:42:16 ERROR Transaction failed",
                createdAt = 1_756_400_000_000L
            )
        )
    )

    // --- JSON ---

    @Test
    fun `el JSON exportado es parseable y contiene todas las secciones`() {
        val text = ProjectJsonExporter.export(bundle)
        val root = JsonParser.parseObjectOrNull(text)

        assertNotNull(root)
        assertNotNull(root!!["project"])
        assertEquals(2, Json.array(root["testCases"]).size)
        assertEquals(1, Json.array(root["executions"]).size)
        assertEquals(1, Json.array(root["bugs"]).size)
        assertEquals(1, Json.array(root["evidence"]).size)
        assertEquals(1, Json.array(root["logs"]).size)
        assertEquals(ProjectJsonExporter.SCHEMA_VERSION, Json.int(root["schemaVersion"]))
    }

    @Test
    fun `el respaldo JSON hace round trip sin perder datos`() {
        val restored = ProjectJsonExporter.import(ProjectJsonExporter.export(bundle))

        assertNotNull(restored)
        assertEquals(bundle.project, restored!!.project)
        assertEquals(bundle.testCases, restored.testCases)
        assertEquals(bundle.executions, restored.executions)
        assertEquals(bundle.bugs, restored.bugs)
        assertEquals(bundle.evidences, restored.evidences)
        assertEquals(bundle.logs, restored.logs)
    }

    @Test
    fun `el resumen del JSON refleja los estados reales`() {
        val root = JsonParser.parseObjectOrNull(ProjectJsonExporter.export(bundle))!!
        val summary = root["summary"] as JsonValue.Obj

        assertEquals(2, Json.int(summary["total"]))
        assertEquals(1, Json.int(summary["pass"]))
        assertEquals(1, Json.int(summary["fail"]))
        assertEquals(50.0, Json.double(summary["passRate"]), 0.0001)
    }

    @Test
    fun `importar un texto invalido devuelve null en vez de lanzar`() {
        assertNull(ProjectJsonExporter.import("no es json"))
        assertNull(ProjectJsonExporter.import("{"))
        assertNull(ProjectJsonExporter.import("[]"))
        assertNull(ProjectJsonExporter.import("{\"project\":{}}"))
    }

    @Test
    fun `las cadenas con caracteres especiales se escapan y se recuperan`() {
        val raw = "linea1\nlinea2\ttab \"comillas\" \\barra"
        val text = Json.stringify(Json.obj("v" to Json.of(raw)))
        val parsed = JsonParser.parseObjectOrNull(text)

        assertNotNull(parsed)
        assertEquals(raw, Json.string(parsed!!["v"]))
    }

    @Test
    fun `los enteros se serializan sin parte decimal`() {
        assertEquals("128", Json.stringify(Json.of(128L)))
        assertEquals("72.5", Json.stringify(Json.of(72.5)))
        assertEquals("0", Json.stringify(Json.of(Double.NaN)))
    }

    @Test
    fun `el parser rechaza entradas truncadas y basura al final`() {
        assertNull(JsonParser.parseOrNull("{\"a\": 1"))
        assertNull(JsonParser.parseOrNull("{\"a\": 1} basura"))
        assertNull(JsonParser.parseOrNull(""))
        assertNotNull(JsonParser.parseOrNull("{\"a\": [1, 2, {\"b\": null}]}"))
    }

    // --- CSV ---

    @Test
    fun `el CSV incluye encabezado y una fila por caso`() {
        val csv = CsvExporter.exportTestCases(bundle)
        val lines = csv.trimEnd().split("\r\n")

        assertTrue(lines[0].startsWith("ID,Titulo,Modulo"))
        assertTrue(csv.contains("TC-001"))
        assertTrue(csv.contains("TC-004"))
        assertTrue(csv.contains("CRITICAL"))
    }

    @Test
    fun `el CSV escapa comas comillas y saltos de linea`() {
        assertEquals("simple", CsvExporter.escape("simple"))
        assertEquals("\"con,coma\"", CsvExporter.escape("con,coma"))
        assertEquals("\"con \"\"comillas\"\"\"", CsvExporter.escape("con \"comillas\""))
        assertEquals("\"linea1\nlinea2\"", CsvExporter.escape("linea1\nlinea2"))
    }

    @Test
    fun `el CSV de defectos contiene los campos del bug`() {
        val csv = CsvExporter.exportBugs(bundle)

        assertTrue(csv.contains("BUG-001"))
        assertTrue(csv.contains("Terminal congelada durante CTLS"))
        assertTrue(csv.contains("OPEN"))
    }

    // --- TXT ---

    @Test
    fun `el reporte TXT contiene las secciones obligatorias`() {
        val txt = TxtExporter.exportReport(bundle)

        assertTrue(txt.contains("QA TEST REPORT"))
        assertTrue(txt.contains("RESUMEN EJECUTIVO"))
        assertTrue(txt.contains("INFORMACION DEL AMBIENTE"))
        assertTrue(txt.contains("MATRIZ DE PRUEBAS"))
        assertTrue(txt.contains("DETALLE DE PRUEBAS FALLIDAS"))
        assertTrue(txt.contains("DEFECTOS REGISTRADOS"))
        assertTrue(txt.contains("CONCLUSION"))
        assertTrue(txt.contains("Terminal P630 QA"))
        assertTrue(txt.contains("PASS RATE: 50%"))
    }

    @Test
    fun `el reporte TXT puede omitir la conclusion automatica`() {
        val txt = TxtExporter.exportReport(bundle, includeConclusion = false)

        assertFalse(txt.contains("CONCLUSION"))
    }

    @Test
    fun `exportar un log produce su contenido integro`() {
        val txt = TxtExporter.exportLog(bundle.logs.first())

        assertTrue(txt.contains("Logcat CTLS"))
        assertTrue(txt.contains("08:42:16 ERROR Transaction failed"))
    }
}
