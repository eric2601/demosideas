package com.qareport.generator.domain.report

import com.qareport.generator.domain.model.ProjectStats
import java.util.Locale

/**
 * Genera la conclusión del reporte EXCLUSIVAMENTE a partir de los datos registrados.
 *
 * Reglas explícitas (no se infiere ni se inventa nada):
 *  - Si no hay casos de prueba no se emite ningún juicio.
 *  - Si quedan casos sin ejecutar se indica que la cobertura está incompleta.
 *  - La recomendación de liberación se deriva de umbrales fijos y documentados sobre
 *    números reales: defectos bloqueantes/críticos, fallos y porcentaje de éxito.
 */
object ConclusionGenerator {

    /** Umbral de pass rate a partir del cual la versión se considera apta. */
    const val PASS_RATE_APPROVED = 95.0

    /** Umbral bajo el cual se recomienda explícitamente no liberar. */
    const val PASS_RATE_REJECTED = 80.0

    enum class Recommendation(val label: String) {
        NO_DATA("Sin datos suficientes"),
        INCOMPLETE("Ejecución incompleta"),
        APPROVED("Apta para liberación"),
        APPROVED_WITH_RESERVATIONS("Apta con reservas"),
        REJECTED("Requiere correcciones")
    }

    data class Conclusion(
        val recommendation: Recommendation,
        val text: String
    )

    fun generate(stats: ProjectStats): Conclusion {
        if (stats.total == 0) {
            return Conclusion(
                recommendation = Recommendation.NO_DATA,
                text = "No se registraron casos de prueba para este proyecto, " +
                    "por lo que no es posible emitir una conclusión sobre la calidad de la versión."
            )
        }

        val recommendation = resolveRecommendation(stats)
        val builder = StringBuilder()

        builder.append(
            "Se registraron ${stats.total} ${plural(stats.total, "caso", "casos")} de prueba, " +
                "de los cuales ${stats.executed} ${plural(stats.executed, "fue ejecutado", "fueron ejecutados")}."
        )
        builder.append('\n')

        builder.append(
            "Se obtuvieron ${stats.passed} ${plural(stats.passed, "resultado", "resultados")} PASS y " +
                "${stats.failed} FAIL."
        )
        if (stats.blocked > 0) {
            builder.append(" Se registraron ${stats.blocked} ${plural(stats.blocked, "caso bloqueado", "casos bloqueados")}.")
        }
        if (stats.skipped > 0) {
            builder.append(" Se omitieron ${stats.skipped} ${plural(stats.skipped, "caso", "casos")}.")
        }
        builder.append('\n')

        builder.append(
            "El porcentaje de éxito fue del ${formatPercent(stats.passRate)} " +
                "sobre el total de casos registrados."
        )
        builder.append('\n')

        if (stats.notTested > 0) {
            builder.append(
                "Quedaron ${stats.notTested} ${plural(stats.notTested, "caso sin ejecutar", "casos sin ejecutar")}, " +
                    "por lo que la cobertura de pruebas está incompleta."
            )
            builder.append('\n')
        }

        builder.append(describeBugs(stats))
        builder.append('\n')
        builder.append(describeRecommendation(recommendation))

        return Conclusion(recommendation = recommendation, text = builder.toString().trim())
    }

    private fun resolveRecommendation(stats: ProjectStats): Recommendation = when {
        stats.notTested > 0 -> Recommendation.INCOMPLETE
        stats.bugsBlocker > 0 || stats.bugsCritical > 0 -> Recommendation.REJECTED
        stats.failed > 0 && stats.passRate < PASS_RATE_REJECTED -> Recommendation.REJECTED
        stats.failed > 0 || stats.blocked > 0 || stats.passRate < PASS_RATE_APPROVED ->
            Recommendation.APPROVED_WITH_RESERVATIONS
        else -> Recommendation.APPROVED
    }

    private fun describeBugs(stats: ProjectStats): String {
        if (stats.bugsTotal == 0) {
            return "No se registraron defectos durante la ejecución."
        }
        val parts = mutableListOf<String>()
        if (stats.bugsBlocker > 0) parts += "${stats.bugsBlocker} ${plural(stats.bugsBlocker, "bloqueante", "bloqueantes")}"
        if (stats.bugsCritical > 0) parts += "${stats.bugsCritical} ${plural(stats.bugsCritical, "crítico", "críticos")}"
        if (stats.bugsMajor > 0) parts += "${stats.bugsMajor} ${plural(stats.bugsMajor, "mayor", "mayores")}"
        if (stats.bugsMinor > 0) parts += "${stats.bugsMinor} ${plural(stats.bugsMinor, "menor", "menores")}"

        val detail = if (parts.isEmpty()) "" else ": " + parts.joinToString(", ") + "."
        val openDetail = if (stats.bugsOpen > 0) {
            " ${stats.bugsOpen} ${plural(stats.bugsOpen, "defecto continúa", "defectos continúan")} sin cerrar."
        } else {
            " Todos los defectos registrados se encuentran cerrados o rechazados."
        }
        return "Se identificaron ${stats.bugsTotal} ${plural(stats.bugsTotal, "defecto", "defectos")}$detail$openDetail"
    }

    private fun describeRecommendation(recommendation: Recommendation): String = when (recommendation) {
        Recommendation.NO_DATA ->
            "No hay información suficiente para emitir una recomendación."
        Recommendation.INCOMPLETE ->
            "Con base en los resultados registrados, la ejecución de pruebas está incompleta, " +
                "por lo que no es posible emitir una recomendación definitiva de liberación."
        Recommendation.REJECTED ->
            "Con base en los resultados registrados, la versión requiere correcciones antes de " +
                "considerarse apta para liberación."
        Recommendation.APPROVED_WITH_RESERVATIONS ->
            "Con base en los resultados registrados, la versión puede considerarse apta para liberación " +
                "con reservas, sujeta a la corrección de los defectos reportados."
        Recommendation.APPROVED ->
            "Con base en los resultados registrados, la versión se considera apta para liberación."
    }

    private fun plural(count: Int, singular: String, plural: String): String =
        if (count == 1) singular else plural

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.0f%%", value)
}
