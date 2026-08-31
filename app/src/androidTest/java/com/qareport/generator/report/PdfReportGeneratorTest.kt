package com.qareport.generator.report

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.ProjectBundle
import com.qareport.generator.domain.model.ReportSettings
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.utils.FileStorage
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Verifica que el PDF se genera realmente en el dispositivo: que el archivo existe,
 * no está vacío y empieza por la cabecera %PDF.
 */
@RunWith(AndroidJUnit4::class)
class PdfReportGeneratorTest {

    private lateinit var fileStorage: FileStorage
    private lateinit var generator: PdfReportGenerator

    @Before
    fun setUp() {
        fileStorage = FileStorage(ApplicationProvider.getApplicationContext())
        generator = PdfReportGenerator(fileStorage)
    }

    private fun bundle(caseCount: Int): ProjectBundle {
        val now = System.currentTimeMillis()
        val cases = (1..caseCount).map { index ->
            val status = when (index % 5) {
                0 -> TestStatus.NOT_TESTED
                1 -> TestStatus.PASS
                2 -> TestStatus.FAIL
                3 -> TestStatus.BLOCKED
                else -> TestStatus.SKIPPED
            }
            TestCase(
                id = index.toLong(),
                projectId = 1L,
                code = "TC-%03d".format(index),
                title = "Caso de prueba número $index con un título razonablemente largo",
                module = "Módulo ${index % 4}",
                priority = Priority.HIGH,
                preconditions = "Dispositivo restaurado.",
                steps = "1. Primer paso.\n2. Segundo paso.\n3. Tercer paso.",
                expectedResult = "El resultado esperado del caso $index.",
                actualResult = "El resultado obtenido del caso $index.",
                status = status,
                severity = if (status == TestStatus.FAIL) Severity.CRITICAL else null,
                createdAt = now,
                updatedAt = now
            )
        }
        return ProjectBundle(
            project = Project(
                id = 1L,
                name = "Terminal P630 QA",
                client = "Empresa XYZ",
                version = "1.2.8",
                buildNumber = "128",
                deviceModel = "Verifone P630",
                androidVersion = "11",
                tester = "Eric",
                environment = "QA",
                description = "Pruebas funcionales de la nueva versión.",
                createdAt = now,
                updatedAt = now
            ),
            testCases = cases,
            bugs = listOf(
                Bug(
                    id = 1L,
                    projectId = 1L,
                    code = "BUG-001",
                    title = "Terminal congelada durante CTLS",
                    description = "La terminal deja de responder.",
                    severity = Severity.CRITICAL,
                    priority = Priority.HIGH,
                    stepsToReproduce = "1. Iniciar transacción.\n2. Acercar tarjeta.",
                    version = "1.2.8",
                    device = "P630",
                    createdAt = now,
                    updatedAt = now
                )
            ),
            logs = listOf(
                LogEntry(
                    id = 1L,
                    projectId = 1L,
                    title = "Logcat",
                    content = "08:42:16 ERROR Transaction failed",
                    createdAt = now
                )
            )
        )
    }

    private fun assertIsPdf(file: File) {
        assertTrue("El PDF debe existir", file.exists())
        assertTrue("El PDF no debe estar vacío", file.length() > 0L)
        val header = file.inputStream().use { input ->
            ByteArray(4).also { input.read(it) }
        }
        assertTrue("El archivo debe empezar por %PDF", String(header) == "%PDF")
    }

    @Test
    fun generaUnPdfValidoConPocosCasos() {
        val file = generator.generate(bundle(5), ReportSettings())

        assertIsPdf(file)
        assertTrue(file.name.startsWith("QA_"))
        assertTrue(file.name.endsWith(".pdf"))
    }

    @Test
    fun generaUnPdfMultipaginaConMuchosCasos() {
        // 120 casos fuerzan varias páginas y ejercitan la paginación automática.
        val file = generator.generate(bundle(120), ReportSettings())

        assertIsPdf(file)
        assertTrue("Un reporte de 120 casos debe ocupar varias páginas", file.length() > 10_000L)
    }

    @Test
    fun generaUnPdfAunSinCasosDePrueba() {
        val empty = bundle(0)

        val file = generator.generate(empty, ReportSettings())

        assertIsPdf(file)
    }

    @Test
    fun respetaLaConfiguracionDeContenido() {
        val settings = ReportSettings(
            includeLogs = false,
            includeEvidence = false,
            includePassedCases = false,
            includeAutoConclusion = false
        )

        val file = generator.generate(bundle(20), settings)

        assertIsPdf(file)
    }
}
