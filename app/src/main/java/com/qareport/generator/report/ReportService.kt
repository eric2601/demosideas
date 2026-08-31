package com.qareport.generator.report

import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.repository.BugRepository
import com.qareport.generator.data.repository.EvidenceRepository
import com.qareport.generator.data.repository.ExecutionRepository
import com.qareport.generator.data.repository.LogRepository
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.data.repository.ReportRepository
import com.qareport.generator.data.repository.TestCaseRepository
import com.qareport.generator.domain.export.CsvExporter
import com.qareport.generator.domain.export.ProjectJsonExporter
import com.qareport.generator.domain.export.TxtExporter
import com.qareport.generator.domain.model.ProjectBundle
import com.qareport.generator.domain.model.Report
import com.qareport.generator.domain.model.ReportFormat
import com.qareport.generator.domain.model.ReportSettings
import com.qareport.generator.domain.stats.StatsCalculator
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.util.TextUtils
import com.qareport.generator.domain.validation.ValidationResult
import com.qareport.generator.domain.validation.Validators
import com.qareport.generator.utils.AppLogger
import com.qareport.generator.utils.FileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orquesta la generación del reporte: reúne los datos, aplica las validaciones y
 * delega la escritura en [PdfReportGenerator] o en los exportadores de texto.
 *
 * Toda la E/S ocurre fuera del hilo principal y cualquier fallo se devuelve como
 * [Outcome.Failure] con un mensaje listo para mostrar al usuario.
 */
class ReportService(
    private val projectRepository: ProjectRepository,
    private val testCaseRepository: TestCaseRepository,
    private val executionRepository: ExecutionRepository,
    private val bugRepository: BugRepository,
    private val evidenceRepository: EvidenceRepository,
    private val logRepository: LogRepository,
    private val reportRepository: ReportRepository,
    private val pdfReportGenerator: PdfReportGenerator,
    private val fileStorage: FileStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /** Reúne todos los datos de un proyecto en un único paquete inmutable. */
    suspend fun loadBundle(projectId: Long): ProjectBundle? = withContext(dispatcher) {
        val project = projectRepository.getById(projectId) ?: return@withContext null
        ProjectBundle(
            project = project,
            testCases = testCaseRepository.getByProject(projectId),
            executions = executionRepository.getByProject(projectId),
            bugs = bugRepository.getByProject(projectId),
            evidences = evidenceRepository.getByProject(projectId),
            logs = logRepository.getByProject(projectId)
        )
    }

    /** Comprueba, antes de generar, que el proyecto cumple las reglas del reporte. */
    suspend fun validate(projectId: Long): ValidationResult = withContext(dispatcher) {
        val project = projectRepository.getById(projectId)
        val cases = testCaseRepository.getByProject(projectId)
        val bugs = bugRepository.getByProject(projectId)
        Validators.validateReportGeneration(project, StatsCalculator.compute(cases, bugs))
    }

    suspend fun generatePdf(projectId: Long, settings: ReportSettings): Outcome<Report> =
        withContext(dispatcher) {
            AppLogger.event(AppLogger.Event.REPORT_GENERATION_STARTED, "project=$projectId")

            val validation = validate(projectId)
            if (validation is ValidationResult.Invalid) {
                AppLogger.errorEvent(
                    AppLogger.Event.REPORT_GENERATION_ERROR,
                    "Validación fallida: ${validation.firstMessage}",
                    null
                )
                return@withContext Outcome.Failure(
                    validation.firstMessage ?: "No es posible generar el reporte."
                )
            }

            val bundle = loadBundle(projectId)
                ?: return@withContext Outcome.Failure("El proyecto ya no está disponible.")

            val outcome = safeCall(
                errorMessage = "No se pudo generar el reporte PDF.",
                logEvent = AppLogger.Event.REPORT_GENERATION_ERROR
            ) {
                val file = pdfReportGenerator.generate(bundle, settings)
                val stats = StatsCalculator.compute(bundle.testCases, bundle.bugs)
                val report = Report(
                    projectId = projectId,
                    fileName = file.name,
                    filePath = file.absolutePath,
                    format = ReportFormat.PDF,
                    createdAt = System.currentTimeMillis(),
                    sizeBytes = file.length(),
                    totalTests = stats.total,
                    passRate = stats.passRate
                )
                val id = when (val registered = reportRepository.register(report)) {
                    is Outcome.Success -> registered.data
                    is Outcome.Failure -> throw IllegalStateException(registered.message)
                }
                report.copy(id = id)
            }

            if (outcome is Outcome.Success) {
                AppLogger.event(
                    AppLogger.Event.REPORT_GENERATION_SUCCESS,
                    "project=$projectId file=${outcome.data.fileName}"
                )
            }
            outcome
        }

    /**
     * Exporta el proyecto en un formato de texto. A diferencia del PDF, la exportación
     * no exige que las pruebas estén finalizadas: sirve también como respaldo parcial.
     */
    suspend fun export(projectId: Long, format: ReportFormat, settings: ReportSettings): Outcome<Report> =
        withContext(dispatcher) {
            AppLogger.event(AppLogger.Event.EXPORT_STARTED, "project=$projectId format=${format.key}")

            val bundle = loadBundle(projectId)
                ?: return@withContext Outcome.Failure("El proyecto ya no está disponible.")

            if (bundle.testCases.isEmpty() && format != ReportFormat.JSON) {
                return@withContext Outcome.Failure("Debes agregar al menos un caso de prueba.")
            }

            val outcome = safeCall(
                errorMessage = "No se pudo exportar el proyecto.",
                logEvent = AppLogger.Event.EXPORT_ERROR
            ) {
                val content = when (format) {
                    ReportFormat.CSV -> CsvExporter.exportTestCases(bundle)
                    ReportFormat.JSON -> ProjectJsonExporter.export(bundle)
                    ReportFormat.TXT -> TxtExporter.exportReport(bundle, settings.includeAutoConclusion)
                    ReportFormat.PDF -> throw IllegalArgumentException("Usa generatePdf para el formato PDF.")
                }
                val fileName = buildExportName(bundle, format)
                val file = fileStorage.writeExport(fileName, content, withBom = format == ReportFormat.CSV)
                    ?: throw IllegalStateException("No se pudo escribir el archivo de exportación.")

                registerExport(projectId, file, format, bundle)
            }

            if (outcome is Outcome.Success) {
                AppLogger.event(AppLogger.Event.EXPORT_SUCCESS, "project=$projectId format=${format.key}")
            }
            outcome
        }

    /** Exporta la matriz de defectos como CSV independiente. */
    suspend fun exportBugsCsv(projectId: Long): Outcome<Report> = withContext(dispatcher) {
        val bundle = loadBundle(projectId)
            ?: return@withContext Outcome.Failure("El proyecto ya no está disponible.")
        if (bundle.bugs.isEmpty()) {
            return@withContext Outcome.Failure("No hay defectos registrados para exportar.")
        }

        safeCall("No se pudo exportar la lista de defectos.", AppLogger.Event.EXPORT_ERROR) {
            val fileName = "BUGS_${TextUtils.toSafeFileName(bundle.project.name, "QA")}_" +
                "${DateFormats.fileTimestamp()}.csv"
            val file = fileStorage.writeExport(fileName, CsvExporter.exportBugs(bundle), withBom = true)
                ?: throw IllegalStateException("No se pudo escribir el archivo de exportación.")
            registerExport(projectId, file, ReportFormat.CSV, bundle)
        }
    }

    private suspend fun registerExport(
        projectId: Long,
        file: File,
        format: ReportFormat,
        bundle: ProjectBundle
    ): Report {
        val stats = StatsCalculator.compute(bundle.testCases, bundle.bugs)
        val report = Report(
            projectId = projectId,
            fileName = file.name,
            filePath = file.absolutePath,
            format = format,
            createdAt = System.currentTimeMillis(),
            sizeBytes = file.length(),
            totalTests = stats.total,
            passRate = stats.passRate
        )
        val id = when (val registered = reportRepository.register(report)) {
            is Outcome.Success -> registered.data
            is Outcome.Failure -> throw IllegalStateException(registered.message)
        }
        return report.copy(id = id)
    }

    private fun buildExportName(bundle: ProjectBundle, format: ReportFormat): String {
        val name = TextUtils.toSafeFileName(bundle.project.name, "QA")
        val version = TextUtils.toSafeFileName(bundle.project.version.ifBlank { "sv" }, "sv")
        return "QA_${name}_${version}_${DateFormats.fileTimestamp()}.${format.extension}"
    }
}
