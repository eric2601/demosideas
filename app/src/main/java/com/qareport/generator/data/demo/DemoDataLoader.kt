package com.qareport.generator.data.demo

import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.repository.BugRepository
import com.qareport.generator.data.repository.ExecutionRepository
import com.qareport.generator.data.repository.LogRepository
import com.qareport.generator.data.repository.ProjectRepository
import com.qareport.generator.data.repository.TestCaseRepository
import com.qareport.generator.domain.model.TestExecution
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.utils.AppLogger

/**
 * Carga el proyecto de demostración completo: proyecto, casos, ejecuciones,
 * defectos y logs, respetando las claves foráneas y el orden de inserción.
 */
class DemoDataLoader(
    private val projectRepository: ProjectRepository,
    private val testCaseRepository: TestCaseRepository,
    private val executionRepository: ExecutionRepository,
    private val bugRepository: BugRepository,
    private val logRepository: LogRepository
) {

    suspend fun load(): Outcome<Long> = safeCall("No se pudieron cargar los datos de demostración.") {
        val now = System.currentTimeMillis()
        val demoProject = DemoDataProvider.project(now)

        val projectId = when (val created = projectRepository.create(demoProject)) {
            is Outcome.Success -> created.data
            is Outcome.Failure -> throw IllegalStateException(created.message)
        }

        val cases = DemoDataProvider.testCases(projectId, now)
        val caseIds = when (val created = testCaseRepository.createAll(cases)) {
            is Outcome.Success -> created.data
            is Outcome.Failure -> throw IllegalStateException(created.message)
        }

        // Historial de ejecución para los casos que ya tienen un resultado registrado.
        val executions = cases.mapIndexedNotNull { index, testCase ->
            val caseId = caseIds.getOrNull(index) ?: return@mapIndexedNotNull null
            if (!testCase.status.isExecuted) return@mapIndexedNotNull null
            TestExecution(
                projectId = projectId,
                testCaseId = caseId,
                status = testCase.status,
                executedAt = now - (cases.size - index) * HOUR_MILLIS,
                executedBy = demoProject.tester,
                notes = testCase.comments,
                problemDescription = if (testCase.status == TestStatus.FAIL) testCase.actualResult else "",
                severity = testCase.severity,
                stepsToReproduce = if (testCase.status == TestStatus.FAIL) testCase.steps else "",
                expectedResult = testCase.expectedResult,
                actualResult = testCase.actualResult
            )
        }
        executionRepository.recordAll(executions)

        val contactlessId = caseIds.getOrNull(cases.indexOfFirst { it.code == "TC-006" })
        val cancelId = caseIds.getOrNull(cases.indexOfFirst { it.code == "TC-008" })
        bugRepository.createAll(DemoDataProvider.bugs(projectId, contactlessId, cancelId, now))
        logRepository.saveAll(DemoDataProvider.logs(projectId, now))

        AppLogger.event(AppLogger.Event.DEMO_DATA_LOADED, "project=$projectId cases=${caseIds.size}")
        projectId
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
