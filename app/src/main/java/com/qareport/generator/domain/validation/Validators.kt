package com.qareport.generator.domain.validation

import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.ProjectStats
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.util.DateFormats

/**
 * Validaciones de negocio. Funciones puras y totalmente cubiertas por tests unitarios.
 * Los mensajes están en español y son los que ve el usuario final.
 */
object Validators {

    object Fields {
        const val PROJECT_NAME = "project_name"
        const val PROJECT_VERSION = "project_version"
        const val PROJECT_TESTER = "project_tester"
        const val PROJECT_DATES = "project_dates"
        const val TEST_CODE = "test_code"
        const val TEST_TITLE = "test_title"
        const val TEST_EXPECTED = "test_expected"
        const val FAIL_DESCRIPTION = "fail_description"
        const val FAIL_SEVERITY = "fail_severity"
        const val FAIL_STEPS = "fail_steps"
        const val FAIL_EXPECTED = "fail_expected"
        const val FAIL_ACTUAL = "fail_actual"
        const val BUG_TITLE = "bug_title"
        const val BUG_DESCRIPTION = "bug_description"
        const val BUG_STEPS = "bug_steps"
        const val REPORT = "report"
    }

    fun validateProject(project: Project): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (project.name.isBlank()) {
            errors += ValidationError(Fields.PROJECT_NAME, "El nombre del proyecto es obligatorio.")
        } else if (project.name.trim().length < 3) {
            errors += ValidationError(Fields.PROJECT_NAME, "El nombre del proyecto debe tener al menos 3 caracteres.")
        }

        if (project.version.isBlank()) {
            errors += ValidationError(Fields.PROJECT_VERSION, "La versión del producto es obligatoria.")
        }

        if (project.tester.isBlank()) {
            errors += ValidationError(Fields.PROJECT_TESTER, "El nombre del tester es obligatorio.")
        }

        val start = project.startDate
        val end = project.endDate
        if (start != null && end != null && end < start) {
            errors += ValidationError(
                Fields.PROJECT_DATES,
                "La fecha de finalización no puede ser anterior a la fecha de inicio."
            )
        }

        return ValidationResult.of(errors)
    }

    /**
     * Valida un caso de prueba. [existingCodes] son los códigos ya usados en el mismo
     * proyecto, excluyendo el del propio caso cuando se está editando.
     */
    fun validateTestCase(testCase: TestCase, existingCodes: List<String> = emptyList()): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        val code = testCase.code.trim()
        if (code.isBlank()) {
            errors += ValidationError(Fields.TEST_CODE, "El identificador del caso es obligatorio.")
        } else if (existingCodes.any { it.equals(code, ignoreCase = true) }) {
            errors += ValidationError(Fields.TEST_CODE, "Ya existe un caso con el identificador $code.")
        }

        if (testCase.title.isBlank()) {
            errors += ValidationError(Fields.TEST_TITLE, "El título del caso de prueba es obligatorio.")
        }

        if (testCase.expectedResult.isBlank()) {
            errors += ValidationError(Fields.TEST_EXPECTED, "El resultado esperado es obligatorio.")
        }

        return ValidationResult.of(errors)
    }

    /**
     * Cuando un caso se marca como FAIL, la información de diagnóstico es obligatoria.
     * Para cualquier otro estado no se exige nada adicional.
     */
    fun validateFailDetails(
        status: TestStatus,
        problemDescription: String,
        severity: Severity?,
        stepsToReproduce: String,
        expectedResult: String,
        actualResult: String
    ): ValidationResult {
        if (status != TestStatus.FAIL) return ValidationResult.Valid

        val errors = mutableListOf<ValidationError>()
        if (problemDescription.isBlank()) {
            errors += ValidationError(Fields.FAIL_DESCRIPTION, "La descripción del problema es obligatoria.")
        }
        if (severity == null) {
            errors += ValidationError(Fields.FAIL_SEVERITY, "Debes seleccionar la severidad del fallo.")
        }
        if (stepsToReproduce.isBlank()) {
            errors += ValidationError(Fields.FAIL_STEPS, "Los pasos para reproducir son obligatorios.")
        }
        if (expectedResult.isBlank()) {
            errors += ValidationError(Fields.FAIL_EXPECTED, "El resultado esperado es obligatorio.")
        }
        if (actualResult.isBlank()) {
            errors += ValidationError(Fields.FAIL_ACTUAL, "El resultado actual es obligatorio.")
        }
        return ValidationResult.of(errors)
    }

    fun validateBug(title: String, description: String, stepsToReproduce: String): ValidationResult {
        val errors = mutableListOf<ValidationError>()
        if (title.isBlank()) {
            errors += ValidationError(Fields.BUG_TITLE, "El título del defecto es obligatorio.")
        }
        if (description.isBlank()) {
            errors += ValidationError(Fields.BUG_DESCRIPTION, "La descripción del defecto es obligatoria.")
        }
        if (stepsToReproduce.isBlank()) {
            errors += ValidationError(Fields.BUG_STEPS, "Los pasos para reproducir son obligatorios.")
        }
        return ValidationResult.of(errors)
    }

    /**
     * Reglas para permitir la generación del reporte:
     *  1. Debe existir un proyecto seleccionado (id > 0).
     *  2. Debe existir al menos un caso de prueba.
     *  3. Las pruebas deben estar finalizadas: o bien el proyecto está marcado como
     *     finalizado, o bien no queda ningún caso en estado NOT TESTED.
     */
    fun validateReportGeneration(project: Project?, stats: ProjectStats): ValidationResult {
        val errors = mutableListOf<ValidationError>()

        if (project == null || project.id <= 0L) {
            errors += ValidationError(Fields.REPORT, "Debes seleccionar un proyecto.")
            return ValidationResult.of(errors)
        }

        if (stats.total == 0) {
            errors += ValidationError(Fields.REPORT, "Debes agregar al menos un caso de prueba.")
        } else if (!project.finalized && stats.notTested > 0) {
            errors += ValidationError(
                Fields.REPORT,
                "Debes finalizar las pruebas antes de generar el reporte. " +
                    "Quedan ${stats.notTested} casos sin ejecutar."
            )
        }

        return ValidationResult.of(errors)
    }

    /** Valida un porcentaje en el rango 0..100 con tolerancia de redondeo. */
    fun isValidPercentage(value: Double): Boolean = value.isFinite() && value >= 0.0 && value <= 100.0

    /** Valida una fecha escrita por el usuario en formato dd/MM/yyyy. Vacío se considera válido. */
    fun isValidDateInput(text: String): Boolean =
        text.isBlank() || DateFormats.parseDateOrNull(text) != null
}
