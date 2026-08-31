package com.qareport.generator.domain

import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.ProjectStats
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.validation.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    private val validProject = Project(
        id = 1L,
        name = "Terminal P630 QA",
        version = "1.2.8",
        tester = "Eric"
    )

    @Test
    fun `un proyecto completo es valido`() {
        assertTrue(Validators.validateProject(validProject).isValid)
    }

    @Test
    fun `el nombre del proyecto es obligatorio`() {
        val result = Validators.validateProject(validProject.copy(name = "   "))

        assertFalse(result.isValid)
        assertEquals(
            "El nombre del proyecto es obligatorio.",
            result.messageFor(Validators.Fields.PROJECT_NAME)
        )
    }

    @Test
    fun `la fecha de fin no puede ser anterior a la de inicio`() {
        val result = Validators.validateProject(
            validProject.copy(startDate = 2_000L, endDate = 1_000L)
        )

        assertFalse(result.isValid)
        assertNotNull(result.messageFor(Validators.Fields.PROJECT_DATES))
    }

    @Test
    fun `no se permiten identificadores de caso duplicados`() {
        val testCase = TestCase(code = "TC-001", title = "Instalación", expectedResult = "OK")

        val duplicated = Validators.validateTestCase(testCase, existingCodes = listOf("tc-001"))
        val unique = Validators.validateTestCase(testCase, existingCodes = listOf("TC-002"))

        assertFalse(duplicated.isValid)
        assertTrue(unique.isValid)
    }

    @Test
    fun `FAIL exige toda la informacion de diagnostico`() {
        val result = Validators.validateFailDetails(
            status = TestStatus.FAIL,
            problemDescription = "",
            severity = null,
            stepsToReproduce = "",
            expectedResult = "",
            actualResult = ""
        )

        assertFalse(result.isValid)
        assertEquals(5, result.messages.size)
    }

    @Test
    fun `FAIL con toda la informacion es valido`() {
        val result = Validators.validateFailDetails(
            status = TestStatus.FAIL,
            problemDescription = "La terminal se congela",
            severity = Severity.CRITICAL,
            stepsToReproduce = "1. Iniciar transacción",
            expectedResult = "Transacción aprobada",
            actualResult = "Terminal congelada"
        )

        assertTrue(result.isValid)
    }

    @Test
    fun `los estados distintos de FAIL no exigen diagnostico`() {
        listOf(TestStatus.PASS, TestStatus.BLOCKED, TestStatus.SKIPPED, TestStatus.NOT_TESTED)
            .forEach { status ->
                val result = Validators.validateFailDetails(status, "", null, "", "", "")
                assertTrue("$status no debería exigir diagnóstico", result.isValid)
            }
    }

    @Test
    fun `no se puede generar un reporte sin proyecto`() {
        val result = Validators.validateReportGeneration(null, ProjectStats(total = 5, passed = 5))

        assertFalse(result.isValid)
        assertEquals("Debes seleccionar un proyecto.", result.firstMessage)
    }

    @Test
    fun `no se puede generar un reporte vacio`() {
        val result = Validators.validateReportGeneration(validProject, ProjectStats())

        assertFalse(result.isValid)
        assertEquals("Debes agregar al menos un caso de prueba.", result.firstMessage)
    }

    @Test
    fun `no se puede generar un reporte con pruebas pendientes`() {
        val stats = ProjectStats(total = 10, passed = 7, notTested = 3)

        val result = Validators.validateReportGeneration(validProject, stats)

        assertFalse(result.isValid)
        assertTrue(result.firstMessage!!.startsWith("Debes finalizar las pruebas"))
        assertTrue(result.firstMessage!!.contains("3 casos sin ejecutar"))
    }

    @Test
    fun `un proyecto finalizado permite generar el reporte aunque queden pendientes`() {
        val stats = ProjectStats(total = 10, passed = 7, notTested = 3)

        val result = Validators.validateReportGeneration(validProject.copy(finalized = true), stats)

        assertTrue(result.isValid)
        assertNull(result.firstMessage)
    }

    @Test
    fun `todas las pruebas ejecutadas permiten generar el reporte`() {
        val stats = ProjectStats(total = 10, passed = 8, failed = 2)

        assertTrue(Validators.validateReportGeneration(validProject, stats).isValid)
    }

    @Test
    fun `valida porcentajes dentro del rango`() {
        assertTrue(Validators.isValidPercentage(0.0))
        assertTrue(Validators.isValidPercentage(72.5))
        assertTrue(Validators.isValidPercentage(100.0))
        assertFalse(Validators.isValidPercentage(-1.0))
        assertFalse(Validators.isValidPercentage(100.1))
        assertFalse(Validators.isValidPercentage(Double.NaN))
    }

    @Test
    fun `valida fechas escritas por el usuario`() {
        assertTrue(Validators.isValidDateInput(""))
        assertTrue(Validators.isValidDateInput("31/08/2026"))
        assertFalse(Validators.isValidDateInput("31/13/2026"))
        assertFalse(Validators.isValidDateInput("no es una fecha"))
    }
}
