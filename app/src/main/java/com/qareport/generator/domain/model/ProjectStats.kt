package com.qareport.generator.domain.model

/**
 * Estadísticas calculadas de un proyecto. Todos los valores se derivan de los datos
 * registrados; ningún campo se estima ni se inventa.
 */
data class ProjectStats(
    val total: Int = 0,
    val passed: Int = 0,
    val failed: Int = 0,
    val blocked: Int = 0,
    val skipped: Int = 0,
    val notTested: Int = 0,
    val bugsTotal: Int = 0,
    val bugsOpen: Int = 0,
    val bugsBlocker: Int = 0,
    val bugsCritical: Int = 0,
    val bugsMajor: Int = 0,
    val bugsMinor: Int = 0
) {
    /** Casos con un resultado registrado (todo lo que no sea NOT TESTED). */
    val executed: Int get() = passed + failed + blocked + skipped

    val pending: Int get() = notTested

    /**
     * Porcentaje de éxito sobre el total de casos del proyecto, en el rango 0..100.
     * Devuelve 0.0 cuando no hay casos, evitando cualquier división por cero.
     */
    val passRate: Double
        get() = if (total <= 0) 0.0 else (passed.toDouble() * 100.0) / total.toDouble()

    /** Porcentaje de avance de la ejecución, en el rango 0..100. */
    val executionRate: Double
        get() = if (total <= 0) 0.0 else (executed.toDouble() * 100.0) / total.toDouble()

    val passRateRounded: Int get() = Math.round(passRate).toInt()

    val executionRateRounded: Int get() = Math.round(executionRate).toInt()

    val isEmpty: Boolean get() = total == 0

    val allExecuted: Boolean get() = total > 0 && notTested == 0

    fun countOf(status: TestStatus): Int = when (status) {
        TestStatus.PASS -> passed
        TestStatus.FAIL -> failed
        TestStatus.BLOCKED -> blocked
        TestStatus.SKIPPED -> skipped
        TestStatus.NOT_TESTED -> notTested
    }

    fun countOfSeverity(severity: Severity): Int = when (severity) {
        Severity.BLOCKER -> bugsBlocker
        Severity.CRITICAL -> bugsCritical
        Severity.MAJOR -> bugsMajor
        Severity.MINOR -> bugsMinor
    }
}

/** Estadísticas globales mostradas en el Dashboard principal. */
data class GlobalStats(
    val projects: Int = 0,
    val executedTests: Int = 0,
    val passed: Int = 0,
    val failed: Int = 0
)

/** Fila agregada usada por las gráficas "pruebas por módulo". */
data class ModuleStats(
    val module: String,
    val total: Int,
    val passed: Int,
    val failed: Int,
    val blocked: Int,
    val skipped: Int,
    val notTested: Int
)

/** Punto de la gráfica de evolución (casos ejecutados por día). */
data class ExecutionTrendPoint(
    val dayStartMillis: Long,
    val label: String,
    val executed: Int,
    val passed: Int,
    val failed: Int
)
