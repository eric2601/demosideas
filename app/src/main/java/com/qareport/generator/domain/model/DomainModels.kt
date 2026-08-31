package com.qareport.generator.domain.model

/**
 * Modelos de dominio. Son data classes puras de Kotlin: no dependen de Android
 * ni de Room, por lo que pueden probarse con tests unitarios JVM normales.
 * La capa `data` los convierte desde/hacia las entidades de Room mediante mappers.
 *
 * Las fechas se representan como epoch millis (Long) para que el dominio sea
 * independiente del formato de presentación.
 */

data class Project(
    val id: Long = 0L,
    val name: String = "",
    val client: String = "",
    val product: String = "",
    val version: String = "",
    val buildNumber: String = "",
    val deviceType: String = "",
    val deviceModel: String = "",
    val operatingSystem: String = "",
    val androidVersion: String = "",
    val tester: String = "",
    val environment: String = "",
    val startDate: Long? = null,
    val endDate: Long? = null,
    val description: String = "",
    val notes: String = "",
    /** Cuando es true las pruebas se consideran finalizadas y el reporte puede generarse. */
    val finalized: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val displayTitle: String
        get() = buildString {
            append(name.ifBlank { "Proyecto sin nombre" })
            if (version.isNotBlank()) append(" - ").append(version)
        }
}

data class TestCase(
    val id: Long = 0L,
    val projectId: Long = 0L,
    /** Identificador legible tipo TC-001. Único dentro del proyecto. */
    val code: String = "",
    val title: String = "",
    val module: String = "",
    val priority: Priority = Priority.DEFAULT,
    val preconditions: String = "",
    val steps: String = "",
    val expectedResult: String = "",
    val actualResult: String = "",
    val status: TestStatus = TestStatus.DEFAULT,
    val severity: Severity? = null,
    val comments: String = "",
    val orderIndex: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class TestExecution(
    val id: Long = 0L,
    val projectId: Long = 0L,
    val testCaseId: Long = 0L,
    val status: TestStatus = TestStatus.DEFAULT,
    val executedAt: Long = 0L,
    val executedBy: String = "",
    val notes: String = "",
    /** Campos obligatorios cuando el resultado es FAIL. */
    val problemDescription: String = "",
    val severity: Severity? = null,
    val stepsToReproduce: String = "",
    val expectedResult: String = "",
    val actualResult: String = ""
)

data class Bug(
    val id: Long = 0L,
    val projectId: Long = 0L,
    val testCaseId: Long? = null,
    /** Identificador legible tipo BUG-001. */
    val code: String = "",
    val title: String = "",
    val description: String = "",
    val severity: Severity = Severity.DEFAULT,
    val priority: Priority = Priority.DEFAULT,
    val stepsToReproduce: String = "",
    val expectedResult: String = "",
    val actualResult: String = "",
    val version: String = "",
    val device: String = "",
    val status: BugStatus = BugStatus.DEFAULT,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class Evidence(
    val id: Long = 0L,
    val projectId: Long = 0L,
    val testCaseId: Long? = null,
    val bugId: Long? = null,
    val displayName: String = "",
    /**
     * URI de la evidencia. Nunca se guarda el contenido binario en Room:
     * solo la referencia (content:// o file:// dentro del almacenamiento privado)
     * junto con sus metadatos.
     */
    val uri: String = "",
    val type: EvidenceType = EvidenceType.DEFAULT,
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val note: String = "",
    val createdAt: Long = 0L
)

data class LogEntry(
    val id: Long = 0L,
    val projectId: Long = 0L,
    val testCaseId: Long? = null,
    val title: String = "",
    val content: String = "",
    val createdAt: Long = 0L
)

data class Report(
    val id: Long = 0L,
    val projectId: Long = 0L,
    val fileName: String = "",
    /** Ruta absoluta dentro del almacenamiento privado de la app. */
    val filePath: String = "",
    val format: ReportFormat = ReportFormat.PDF,
    val createdAt: Long = 0L,
    val sizeBytes: Long = 0L,
    val totalTests: Int = 0,
    val passRate: Double = 0.0
)

/** Preferencias que afectan el contenido del reporte generado. */
data class ReportSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val includeLogs: Boolean = true,
    val includeEvidence: Boolean = true,
    val includePassedCases: Boolean = true,
    val includeFailedCases: Boolean = true,
    val includeAutoConclusion: Boolean = true,
    val showTechnicalLogs: Boolean = false
)

/**
 * Conjunto completo de datos de un proyecto. Es la entrada única del generador de
 * PDF y de los exportadores, de modo que ninguno de ellos consulta la base de datos
 * por su cuenta ni puede inventar información.
 */
data class ProjectBundle(
    val project: Project,
    val testCases: List<TestCase> = emptyList(),
    val executions: List<TestExecution> = emptyList(),
    val bugs: List<Bug> = emptyList(),
    val evidences: List<Evidence> = emptyList(),
    val logs: List<LogEntry> = emptyList()
) {
    fun evidenceFor(testCaseId: Long): List<Evidence> =
        evidences.filter { it.testCaseId == testCaseId }

    fun evidenceForBug(bugId: Long): List<Evidence> =
        evidences.filter { it.bugId == bugId }

    /** Última ejecución registrada de un caso, o null si nunca se ejecutó. */
    fun lastExecutionOf(testCaseId: Long): TestExecution? =
        executions.filter { it.testCaseId == testCaseId }.maxByOrNull { it.executedAt }
}
