package com.qareport.generator.domain

import com.qareport.generator.domain.model.ProjectStats
import com.qareport.generator.domain.report.ConclusionGenerator
import com.qareport.generator.domain.report.ConclusionGenerator.Recommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConclusionGeneratorTest {

    @Test
    fun `sin casos no emite juicio de liberacion`() {
        val conclusion = ConclusionGenerator.generate(ProjectStats())

        assertEquals(Recommendation.NO_DATA, conclusion.recommendation)
        assertTrue(conclusion.text.contains("No se registraron casos de prueba"))
        assertFalse(conclusion.text.contains("apta para liberación"))
    }

    @Test
    fun `casos sin ejecutar marcan la ejecucion como incompleta`() {
        val stats = ProjectStats(total = 10, passed = 9, notTested = 1)

        val conclusion = ConclusionGenerator.generate(stats)

        assertEquals(Recommendation.INCOMPLETE, conclusion.recommendation)
        assertTrue(conclusion.text.contains("1 caso sin ejecutar"))
        assertTrue(conclusion.text.contains("cobertura de pruebas está incompleta"))
    }

    @Test
    fun `un defecto critico obliga a correcciones`() {
        val stats = ProjectStats(
            total = 100,
            passed = 98,
            failed = 2,
            bugsTotal = 1,
            bugsCritical = 1,
            bugsOpen = 1
        )

        val conclusion = ConclusionGenerator.generate(stats)

        assertEquals(Recommendation.REJECTED, conclusion.recommendation)
        assertTrue(conclusion.text.contains("requiere correcciones"))
    }

    @Test
    fun `un pass rate bajo con fallos obliga a correcciones`() {
        val stats = ProjectStats(total = 100, passed = 60, failed = 40)

        val conclusion = ConclusionGenerator.generate(stats)

        assertEquals(Recommendation.REJECTED, conclusion.recommendation)
    }

    @Test
    fun `todo en verde se considera apto`() {
        val stats = ProjectStats(total = 20, passed = 20)

        val conclusion = ConclusionGenerator.generate(stats)

        assertEquals(Recommendation.APPROVED, conclusion.recommendation)
        assertTrue(conclusion.text.contains("apta para liberación"))
        assertTrue(conclusion.text.contains("No se registraron defectos"))
    }

    @Test
    fun `fallos aislados con pass rate alto quedan aptos con reservas`() {
        val stats = ProjectStats(
            total = 100,
            passed = 90,
            failed = 10,
            bugsTotal = 3,
            bugsMajor = 1,
            bugsMinor = 2,
            bugsOpen = 3
        )

        val conclusion = ConclusionGenerator.generate(stats)

        assertEquals(Recommendation.APPROVED_WITH_RESERVATIONS, conclusion.recommendation)
        assertTrue(conclusion.text.contains("con reservas"))
    }

    @Test
    fun `el texto reproduce exactamente los numeros registrados`() {
        val stats = ProjectStats(
            total = 100,
            passed = 72,
            failed = 15,
            blocked = 5,
            skipped = 3,
            notTested = 5,
            bugsTotal = 18,
            bugsCritical = 2,
            bugsMajor = 7,
            bugsMinor = 9,
            bugsOpen = 18
        )

        val text = ConclusionGenerator.generate(stats).text

        assertTrue(text.contains("Se registraron 100 casos de prueba"))
        assertTrue(text.contains("95 fueron ejecutados"))
        assertTrue(text.contains("72 resultados PASS y 15 FAIL"))
        assertTrue(text.contains("5 casos bloqueados"))
        assertTrue(text.contains("72%"))
        assertTrue(text.contains("Se identificaron 18 defectos"))
        assertTrue(text.contains("2 críticos"))
        assertTrue(text.contains("7 mayores"))
        assertTrue(text.contains("9 menores"))
    }

    @Test
    fun `no menciona severidades que no existen`() {
        val stats = ProjectStats(total = 5, passed = 5, bugsTotal = 1, bugsMinor = 1)

        val text = ConclusionGenerator.generate(stats).text

        assertFalse(text.contains("crítico"))
        assertFalse(text.contains("bloqueante"))
        assertTrue(text.contains("1 menor"))
    }

    @Test
    fun `usa singular cuando hay un solo elemento`() {
        val stats = ProjectStats(total = 1, passed = 1)

        val text = ConclusionGenerator.generate(stats).text

        assertTrue(text.contains("Se registraron 1 caso de prueba"))
        assertTrue(text.contains("1 fue ejecutado"))
    }
}
