package com.qareport.generator.domain.export

import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.domain.model.ProjectBundle
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.report.ConclusionGenerator
import com.qareport.generator.domain.stats.StatsCalculator
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.util.TextUtils

/**
 * Exportación del reporte QA en texto plano. Es el mismo contenido del PDF pero en
 * un formato que puede pegarse en un correo o en un ticket.
 */
object TxtExporter {

    private const val WIDTH = 72
    private val SEPARATOR = "-".repeat(WIDTH)
    private val DOUBLE_SEPARATOR = "=".repeat(WIDTH)

    fun exportReport(bundle: ProjectBundle, includeConclusion: Boolean = true): String {
        val project = bundle.project
        val stats = StatsCalculator.compute(bundle.testCases, bundle.bugs)
        val builder = StringBuilder()

        builder.appendLine(DOUBLE_SEPARATOR)
        builder.appendLine("QA TEST REPORT")
        builder.appendLine(DOUBLE_SEPARATOR)
        builder.appendLine()
        builder.appendLine("Proyecto:  ${TextUtils.orDash(project.name)}")
        builder.appendLine("Cliente:   ${TextUtils.orDash(project.client)}")
        builder.appendLine("Producto:  ${TextUtils.orDash(project.product)}")
        builder.appendLine("Version:   ${TextUtils.orDash(project.version)}")
        builder.appendLine("Build:     ${TextUtils.orDash(project.buildNumber)}")
        builder.appendLine("Tester:    ${TextUtils.orDash(project.tester)}")
        builder.appendLine("Fecha:     ${DateFormats.formatDate(System.currentTimeMillis())}")
        builder.appendLine()

        builder.appendLine(SEPARATOR)
        builder.appendLine("RESUMEN EJECUTIVO")
        builder.appendLine(SEPARATOR)
        builder.appendLine("Total de pruebas: ${stats.total}")
        builder.appendLine("PASS:             ${stats.passed}")
        builder.appendLine("FAIL:             ${stats.failed}")
        builder.appendLine("BLOCKED:          ${stats.blocked}")
        builder.appendLine("SKIPPED:          ${stats.skipped}")
        builder.appendLine("NOT TESTED:       ${stats.notTested}")
        builder.appendLine()
        builder.appendLine("PASS RATE: ${TextUtils.formatPercent(stats.passRate)}")
        builder.appendLine()

        builder.appendLine(SEPARATOR)
        builder.appendLine("INFORMACION DEL AMBIENTE")
        builder.appendLine(SEPARATOR)
        builder.appendLine("Dispositivo:      ${TextUtils.orDash(project.deviceType)}")
        builder.appendLine("Modelo:           ${TextUtils.orDash(project.deviceModel)}")
        builder.appendLine("Sistema:          ${TextUtils.orDash(project.operatingSystem)}")
        builder.appendLine("Version Android:  ${TextUtils.orDash(project.androidVersion)}")
        builder.appendLine("Ambiente:         ${TextUtils.orDash(project.environment)}")
        builder.appendLine("Inicio:           ${DateFormats.formatDate(project.startDate)}")
        builder.appendLine("Fin:              ${DateFormats.formatDate(project.endDate)}")
        builder.appendLine()

        builder.appendLine(SEPARATOR)
        builder.appendLine("MATRIZ DE PRUEBAS")
        builder.appendLine(SEPARATOR)
        builder.appendLine(matrixRow("ID", "CASO", "RESULTADO", "SEVERIDAD"))
        builder.appendLine(SEPARATOR)
        bundle.testCases.forEach { testCase ->
            builder.appendLine(
                matrixRow(
                    testCase.code,
                    TextUtils.ellipsize(testCase.title, 28),
                    testCase.status.label,
                    testCase.severity?.label ?: "-"
                )
            )
        }
        builder.appendLine()

        val failed = bundle.testCases.filter { it.status == TestStatus.FAIL }
        if (failed.isNotEmpty()) {
            builder.appendLine(SEPARATOR)
            builder.appendLine("DETALLE DE PRUEBAS FALLIDAS")
            builder.appendLine(SEPARATOR)
            failed.forEach { testCase ->
                builder.appendLine("${testCase.code} - ${testCase.title}")
                builder.appendLine("  Modulo:              ${TextUtils.orDash(testCase.module)}")
                builder.appendLine("  Severidad:           ${testCase.severity?.label ?: "-"}")
                builder.appendLine("  Resultado esperado:  ${TextUtils.orDash(testCase.expectedResult)}")
                builder.appendLine("  Resultado actual:    ${TextUtils.orDash(testCase.actualResult)}")
                val steps = TextUtils.splitSteps(testCase.steps)
                if (steps.isNotEmpty()) {
                    builder.appendLine("  Pasos:")
                    steps.forEachIndexed { index, step -> builder.appendLine("    ${index + 1}. $step") }
                }
                val evidence = bundle.evidenceFor(testCase.id)
                if (evidence.isNotEmpty()) {
                    builder.appendLine("  Evidencias:")
                    evidence.forEach { builder.appendLine("    - ${it.displayName} (${it.type.label})") }
                }
                builder.appendLine()
            }
        }

        if (bundle.bugs.isNotEmpty()) {
            builder.appendLine(SEPARATOR)
            builder.appendLine("DEFECTOS REGISTRADOS")
            builder.appendLine(SEPARATOR)
            bundle.bugs.forEach { bug ->
                builder.appendLine("${bug.code} - ${bug.title}")
                builder.appendLine("  Severidad:  ${bug.severity.label}")
                builder.appendLine("  Prioridad:  ${bug.priority.label}")
                builder.appendLine("  Estado:     ${bug.status.label}")
                builder.appendLine("  Version:    ${TextUtils.orDash(bug.version)}")
                builder.appendLine("  Dispositivo:${TextUtils.orDash(bug.device)}")
                builder.appendLine("  Descripcion:${TextUtils.orDash(bug.description)}")
                builder.appendLine()
            }
        }

        if (includeConclusion) {
            builder.appendLine(SEPARATOR)
            builder.appendLine("CONCLUSION")
            builder.appendLine(SEPARATOR)
            builder.appendLine(ConclusionGenerator.generate(stats).text)
            builder.appendLine()
        }

        return builder.toString()
    }

    /** Exporta un único bloque de logs a texto plano. */
    fun exportLog(entry: LogEntry): String = buildString {
        appendLine(DOUBLE_SEPARATOR)
        appendLine(entry.title.ifBlank { "LOG" })
        appendLine(DateFormats.formatDateTime(entry.createdAt))
        appendLine(DOUBLE_SEPARATOR)
        appendLine()
        appendLine(entry.content)
    }

    private fun matrixRow(id: String, title: String, result: String, severity: String): String =
        id.padEnd(10).take(10) + " " +
            title.padEnd(30).take(30) + " " +
            result.padEnd(12).take(12) + " " +
            severity
}
