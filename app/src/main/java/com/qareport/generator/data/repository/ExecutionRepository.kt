package com.qareport.generator.data.repository

import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.local.dao.TestCaseDao
import com.qareport.generator.data.local.dao.TestExecutionDao
import com.qareport.generator.data.mapper.toDomain
import com.qareport.generator.data.mapper.toEntity
import com.qareport.generator.domain.model.TestExecution
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Registro de ejecuciones. Cada ejecución queda como histórico y además actualiza el
 * estado actual del caso de prueba, de modo que el Dashboard y el reporte siempre
 * reflejan el último resultado registrado.
 */
class ExecutionRepository(
    private val executionDao: TestExecutionDao,
    private val testCaseDao: TestCaseDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeByProject(projectId: Long): Flow<List<TestExecution>> =
        executionDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeByTestCase(testCaseId: Long): Flow<List<TestExecution>> =
        executionDao.observeByTestCase(testCaseId).map { list -> list.map { it.toDomain() } }

    suspend fun getByProject(projectId: Long): List<TestExecution> = withContext(dispatcher) {
        executionDao.getByProject(projectId).map { it.toDomain() }
    }

    suspend fun getLastForTestCase(testCaseId: Long): TestExecution? = withContext(dispatcher) {
        executionDao.getLastForTestCase(testCaseId)?.toDomain()
    }

    /**
     * Registra un resultado y sincroniza el estado del caso de prueba.
     * [comments] se guarda en el caso; [TestExecution.notes] en el histórico.
     */
    suspend fun record(execution: TestExecution, comments: String = ""): Outcome<Long> =
        withContext(dispatcher) {
            safeCall("No se pudo registrar el resultado de la prueba.") {
                val now = if (execution.executedAt > 0L) execution.executedAt else System.currentTimeMillis()
                val id = executionDao.insert(execution.copy(executedAt = now).toEntity())
                testCaseDao.updateResult(
                    testCaseId = execution.testCaseId,
                    status = execution.status.key,
                    severity = execution.severity?.key,
                    actualResult = execution.actualResult,
                    comments = comments,
                    updatedAt = now
                )
                AppLogger.event(
                    AppLogger.Event.TEST_EXECUTED,
                    "case=${execution.testCaseId} status=${execution.status.key}"
                )
                id
            }
        }

    suspend fun recordAll(executions: List<TestExecution>): Outcome<List<Long>> =
        withContext(dispatcher) {
            safeCall("No se pudieron registrar las ejecuciones.") {
                executionDao.insertAll(executions.map { it.toEntity() })
            }
        }

    suspend fun deleteByProject(projectId: Long): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo limpiar el historial de ejecuciones.") {
            executionDao.deleteByProject(projectId)
        }
    }
}
