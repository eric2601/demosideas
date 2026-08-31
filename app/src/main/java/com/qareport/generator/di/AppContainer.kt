package com.qareport.generator.di

import android.content.Context
import com.qareport.generator.data.demo.DemoDataLoader
import com.qareport.generator.data.local.AppDatabase
import com.qareport.generator.data.prefs.SettingsRepository
import com.qareport.generator.data.repository.BugRepository
import com.qareport.generator.data.repository.EvidenceRepository
import com.qareport.generator.data.repository.ExecutionRepository
import com.qareport.generator.data.repository.LogRepository
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.data.repository.ReportRepository
import com.qareport.generator.data.repository.TestCaseRepository
import com.qareport.generator.report.PdfReportGenerator
import com.qareport.generator.report.ReportService
import com.qareport.generator.utils.FileStorage

/**
 * Contenedor de dependencias manual.
 *
 * Se prefiere a Hilt/Dagger para no añadir un procesador de anotaciones más (KSP ya
 * corre para Room) ni un plugin de Gradle adicional: el grafo de esta aplicación es
 * pequeño, estático y perfectamente legible construido a mano.
 *
 * Todo se crea de forma perezosa (`by lazy`) para no penalizar el arranque.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.getInstance(appContext) }

    val fileStorage: FileStorage by lazy { FileStorage(appContext) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), database.testCaseDao())
    }

    val testCaseRepository: TestCaseRepository by lazy {
        TestCaseRepository(database.testCaseDao())
    }

    val executionRepository: ExecutionRepository by lazy {
        ExecutionRepository(database.testExecutionDao(), database.testCaseDao())
    }

    val bugRepository: BugRepository by lazy { BugRepository(database.bugDao()) }

    val evidenceRepository: EvidenceRepository by lazy {
        EvidenceRepository(database.evidenceDao(), fileStorage)
    }

    val logRepository: LogRepository by lazy { LogRepository(database.logEntryDao()) }

    val reportRepository: ReportRepository by lazy {
        ReportRepository(database.reportDao(), fileStorage)
    }

    private val pdfReportGenerator: PdfReportGenerator by lazy { PdfReportGenerator(fileStorage) }

    val reportService: ReportService by lazy {
        ReportService(
            projectRepository = projectRepository,
            testCaseRepository = testCaseRepository,
            executionRepository = executionRepository,
            bugRepository = bugRepository,
            evidenceRepository = evidenceRepository,
            logRepository = logRepository,
            reportRepository = reportRepository,
            pdfReportGenerator = pdfReportGenerator,
            fileStorage = fileStorage
        )
    }

    val demoDataLoader: DemoDataLoader by lazy {
        DemoDataLoader(
            projectRepository = projectRepository,
            testCaseRepository = testCaseRepository,
            executionRepository = executionRepository,
            bugRepository = bugRepository,
            logRepository = logRepository
        )
    }
}
