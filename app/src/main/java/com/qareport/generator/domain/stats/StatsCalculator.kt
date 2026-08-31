package com.qareport.generator.domain.stats

import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.ExecutionTrendPoint
import com.qareport.generator.domain.model.ModuleStats
import com.qareport.generator.domain.model.ProjectStats
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestExecution
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.util.DateFormats
import java.util.Calendar
import java.util.Locale

/**
 * Cálculo determinista de estadísticas. Funciones puras, sin estado ni dependencias
 * de Android, para poder cubrirlas con tests unitarios.
 */
object StatsCalculator {

    fun compute(testCases: List<TestCase>, bugs: List<Bug> = emptyList()): ProjectStats {
        var passed = 0
        var failed = 0
        var blocked = 0
        var skipped = 0
        var notTested = 0

        for (case in testCases) {
            when (case.status) {
                TestStatus.PASS -> passed++
                TestStatus.FAIL -> failed++
                TestStatus.BLOCKED -> blocked++
                TestStatus.SKIPPED -> skipped++
                TestStatus.NOT_TESTED -> notTested++
            }
        }

        return ProjectStats(
            total = testCases.size,
            passed = passed,
            failed = failed,
            blocked = blocked,
            skipped = skipped,
            notTested = notTested,
            bugsTotal = bugs.size,
            bugsOpen = bugs.count { it.status.isOpenIssue },
            bugsBlocker = bugs.count { it.severity == Severity.BLOCKER },
            bugsCritical = bugs.count { it.severity == Severity.CRITICAL },
            bugsMajor = bugs.count { it.severity == Severity.MAJOR },
            bugsMinor = bugs.count { it.severity == Severity.MINOR }
        )
    }

    /** Agrupa los casos por módulo. Los casos sin módulo se agrupan bajo "Sin módulo". */
    fun byModule(testCases: List<TestCase>): List<ModuleStats> =
        testCases
            .groupBy { it.module.trim().ifBlank { "Sin módulo" } }
            .map { (module, cases) ->
                ModuleStats(
                    module = module,
                    total = cases.size,
                    passed = cases.count { it.status == TestStatus.PASS },
                    failed = cases.count { it.status == TestStatus.FAIL },
                    blocked = cases.count { it.status == TestStatus.BLOCKED },
                    skipped = cases.count { it.status == TestStatus.SKIPPED },
                    notTested = cases.count { it.status == TestStatus.NOT_TESTED }
                )
            }
            .sortedWith(compareByDescending<ModuleStats> { it.total }.thenBy { it.module })

    /**
     * Evolución de la ejecución agrupada por día natural.
     * Solo considera ejecuciones reales; si no hay ninguna, devuelve una lista vacía.
     */
    fun executionTrend(executions: List<TestExecution>): List<ExecutionTrendPoint> {
        if (executions.isEmpty()) return emptyList()
        val calendar = Calendar.getInstance(Locale.getDefault())
        return executions
            .groupBy { execution ->
                calendar.timeInMillis = execution.executedAt
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            .toSortedMap()
            .map { (dayStart, dayExecutions) ->
                ExecutionTrendPoint(
                    dayStartMillis = dayStart,
                    label = DateFormats.formatShortDate(dayStart),
                    executed = dayExecutions.size,
                    passed = dayExecutions.count { it.status == TestStatus.PASS },
                    failed = dayExecutions.count { it.status == TestStatus.FAIL }
                )
            }
    }

    /** Siguiente código correlativo con el prefijo indicado, p. ej. "TC-011" o "BUG-003". */
    fun nextCode(prefix: String, existingCodes: List<String>, padding: Int = 3): String {
        val normalizedPrefix = prefix.trim().uppercase(Locale.ROOT)
        val maxNumber = existingCodes
            .asSequence()
            .map { it.trim().uppercase(Locale.ROOT) }
            .filter { it.startsWith("$normalizedPrefix-") }
            .mapNotNull { it.removePrefix("$normalizedPrefix-").toIntOrNull() }
            .maxOrNull() ?: 0
        return "$normalizedPrefix-" + (maxNumber + 1).toString().padStart(padding, '0')
    }
}
