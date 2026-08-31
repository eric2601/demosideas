package com.qareport.generator.domain

import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.stats.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsCalculatorTest {

    private fun case(status: TestStatus, module: String = "General", code: String = "TC-000") =
        TestCase(code = code, title = "Caso", module = module, status = status)

    @Test
    fun `sin casos las estadisticas quedan vacias y el passRate es cero`() {
        val stats = StatsCalculator.compute(emptyList())

        assertEquals(0, stats.total)
        assertEquals(0.0, stats.passRate, 0.0001)
        assertEquals(0.0, stats.executionRate, 0.0001)
        assertTrue(stats.isEmpty)
        assertFalse(stats.allExecuted)
    }

    @Test
    fun `cuenta cada estado por separado`() {
        val cases = listOf(
            case(TestStatus.PASS),
            case(TestStatus.PASS),
            case(TestStatus.FAIL),
            case(TestStatus.BLOCKED),
            case(TestStatus.SKIPPED),
            case(TestStatus.NOT_TESTED)
        )

        val stats = StatsCalculator.compute(cases)

        assertEquals(6, stats.total)
        assertEquals(2, stats.passed)
        assertEquals(1, stats.failed)
        assertEquals(1, stats.blocked)
        assertEquals(1, stats.skipped)
        assertEquals(1, stats.notTested)
        assertEquals(5, stats.executed)
        assertEquals(1, stats.pending)
    }

    @Test
    fun `el passRate se calcula sobre el total de casos`() {
        val cases = List(72) { case(TestStatus.PASS) } +
            List(15) { case(TestStatus.FAIL) } +
            List(5) { case(TestStatus.BLOCKED) } +
            List(3) { case(TestStatus.SKIPPED) } +
            List(5) { case(TestStatus.NOT_TESTED) }

        val stats = StatsCalculator.compute(cases)

        assertEquals(100, stats.total)
        assertEquals(72.0, stats.passRate, 0.0001)
        assertEquals(72, stats.passRateRounded)
        assertEquals(95, stats.executionRateRounded)
    }

    @Test
    fun `cuenta los defectos por severidad y los abiertos`() {
        val bugs = listOf(
            Bug(code = "BUG-001", severity = Severity.CRITICAL, status = BugStatus.OPEN),
            Bug(code = "BUG-002", severity = Severity.CRITICAL, status = BugStatus.CLOSED),
            Bug(code = "BUG-003", severity = Severity.MAJOR, status = BugStatus.IN_PROGRESS),
            Bug(code = "BUG-004", severity = Severity.MINOR, status = BugStatus.REJECTED),
            Bug(code = "BUG-005", severity = Severity.BLOCKER, status = BugStatus.RETEST)
        )

        val stats = StatsCalculator.compute(listOf(case(TestStatus.PASS)), bugs)

        assertEquals(5, stats.bugsTotal)
        assertEquals(1, stats.bugsBlocker)
        assertEquals(2, stats.bugsCritical)
        assertEquals(1, stats.bugsMajor)
        assertEquals(1, stats.bugsMinor)
        // OPEN, IN_PROGRESS y RETEST siguen abiertos; CLOSED y REJECTED no.
        assertEquals(3, stats.bugsOpen)
    }

    @Test
    fun `allExecuted solo es cierto cuando no quedan casos NOT TESTED`() {
        assertTrue(StatsCalculator.compute(listOf(case(TestStatus.PASS))).allExecuted)
        assertFalse(StatsCalculator.compute(listOf(case(TestStatus.NOT_TESTED))).allExecuted)
    }

    @Test
    fun `agrupa por modulo y ordena por cantidad descendente`() {
        val cases = listOf(
            case(TestStatus.PASS, module = "Pagos"),
            case(TestStatus.FAIL, module = "Pagos"),
            case(TestStatus.PASS, module = "Pagos"),
            case(TestStatus.PASS, module = "Login"),
            case(TestStatus.PASS, module = "   ")
        )

        val modules = StatsCalculator.byModule(cases)

        assertEquals(3, modules.size)
        assertEquals("Pagos", modules[0].module)
        assertEquals(3, modules[0].total)
        assertEquals(2, modules[0].passed)
        assertEquals(1, modules[0].failed)
        assertTrue(modules.any { it.module == "Sin módulo" })
    }

    @Test
    fun `nextCode continua la numeracion existente`() {
        assertEquals("TC-001", StatsCalculator.nextCode("TC", emptyList()))
        assertEquals("TC-011", StatsCalculator.nextCode("TC", listOf("TC-001", "TC-010", "TC-007")))
        assertEquals("BUG-004", StatsCalculator.nextCode("BUG", listOf("BUG-003", "TC-099")))
    }

    @Test
    fun `nextCode ignora codigos con formato invalido`() {
        assertEquals("TC-003", StatsCalculator.nextCode("TC", listOf("TC-002", "TC-abc", "otro")))
    }
}
