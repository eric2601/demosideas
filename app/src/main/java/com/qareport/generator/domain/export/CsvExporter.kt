package com.qareport.generator.domain.export

import com.qareport.generator.domain.model.ProjectBundle
import com.qareport.generator.domain.util.DateFormats

/**
 * Exportación CSV (RFC 4180) de los casos de prueba y de los defectos.
 *
 * Se antepone el BOM UTF-8 al escribir el archivo (ver `FileExportManager`) para que
 * Excel muestre correctamente los acentos.
 */
object CsvExporter {

    private const val SEPARATOR = ','
    private const val LINE_BREAK = "\r\n"

    private val TEST_CASE_HEADERS = listOf(
        "ID", "Titulo", "Modulo", "Prioridad", "Estado", "Severidad",
        "Precondiciones", "Pasos", "Resultado esperado", "Resultado obtenido", "Comentarios"
    )

    private val BUG_HEADERS = listOf(
        "ID", "Titulo", "Severidad", "Prioridad", "Estado", "Descripcion",
        "Pasos para reproducir", "Resultado esperado", "Resultado actual",
        "Version", "Dispositivo", "Fecha"
    )

    fun exportTestCases(bundle: ProjectBundle): String {
        val builder = StringBuilder()
        builder.append(row(TEST_CASE_HEADERS))
        bundle.testCases.forEach { testCase ->
            builder.append(
                row(
                    listOf(
                        testCase.code,
                        testCase.title,
                        testCase.module,
                        testCase.priority.label,
                        testCase.status.label,
                        testCase.severity?.label.orEmpty(),
                        testCase.preconditions,
                        testCase.steps,
                        testCase.expectedResult,
                        testCase.actualResult,
                        testCase.comments
                    )
                )
            )
        }
        return builder.toString()
    }

    fun exportBugs(bundle: ProjectBundle): String {
        val builder = StringBuilder()
        builder.append(row(BUG_HEADERS))
        bundle.bugs.forEach { bug ->
            builder.append(
                row(
                    listOf(
                        bug.code,
                        bug.title,
                        bug.severity.label,
                        bug.priority.label,
                        bug.status.label,
                        bug.description,
                        bug.stepsToReproduce,
                        bug.expectedResult,
                        bug.actualResult,
                        bug.version,
                        bug.device,
                        DateFormats.formatDateTime(bug.createdAt)
                    )
                )
            )
        }
        return builder.toString()
    }

    private fun row(values: List<String>): String =
        values.joinToString(SEPARATOR.toString()) { escape(it) } + LINE_BREAK

    /**
     * Encierra el valor entre comillas cuando contiene separador, comillas o saltos de
     * línea, y duplica las comillas internas, tal como exige RFC 4180.
     */
    fun escape(raw: String): String {
        val value = raw.replace("\r\n", "\n")
        val needsQuotes = value.contains(SEPARATOR) ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        return if (needsQuotes) '"' + value.replace("\"", "\"\"") + '"' else value
    }
}
