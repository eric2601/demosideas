package com.qareport.generator.data.repository

import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.local.dao.TestCaseDao
import com.qareport.generator.data.mapper.toDomain
import com.qareport.generator.data.mapper.toEntity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.stats.StatsCalculator
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TestCaseRepository(
    private val testCaseDao: TestCaseDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeByProject(projectId: Long): Flow<List<TestCase>> =
        testCaseDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeById(testCaseId: Long): Flow<TestCase?> =
        testCaseDao.observeById(testCaseId).map { it?.toDomain() }

    suspend fun getByProject(projectId: Long): List<TestCase> = withContext(dispatcher) {
        testCaseDao.getByProject(projectId).map { it.toDomain() }
    }

    suspend fun getById(testCaseId: Long): TestCase? = withContext(dispatcher) {
        testCaseDao.getById(testCaseId)?.toDomain()
    }

    suspend fun getCodes(projectId: Long): List<String> = withContext(dispatcher) {
        testCaseDao.getCodes(projectId)
    }

    /** Sugiere el siguiente identificador libre del proyecto, p. ej. TC-011. */
    suspend fun suggestNextCode(projectId: Long): String = withContext(dispatcher) {
        StatsCalculator.nextCode("TC", testCaseDao.getCodes(projectId))
    }

    suspend fun create(testCase: TestCase): Outcome<Long> = withContext(dispatcher) {
        safeCall("No se pudo crear el caso de prueba.") {
            val now = System.currentTimeMillis()
            val orderIndex = testCaseDao.getMaxOrderIndex(testCase.projectId) + 1
            val id = testCaseDao.insert(
                testCase.copy(createdAt = now, updatedAt = now, orderIndex = orderIndex).toEntity()
            )
            AppLogger.event(AppLogger.Event.TEST_CREATED, "id=$id project=${testCase.projectId}")
            id
        }
    }

    suspend fun createAll(testCases: List<TestCase>): Outcome<List<Long>> = withContext(dispatcher) {
        safeCall("No se pudieron crear los casos de prueba.") {
            val now = System.currentTimeMillis()
            val ids = testCaseDao.insertAll(
                testCases.mapIndexed { index, testCase ->
                    testCase.copy(createdAt = now, updatedAt = now, orderIndex = index).toEntity()
                }
            )
            AppLogger.event(AppLogger.Event.TEST_CREATED, "count=${ids.size}")
            ids
        }
    }

    suspend fun update(testCase: TestCase): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo guardar el caso de prueba.") {
            testCaseDao.update(testCase.copy(updatedAt = System.currentTimeMillis()).toEntity())
            AppLogger.event(AppLogger.Event.TEST_UPDATED, "id=${testCase.id}")
        }
    }

    suspend fun delete(testCaseId: Long): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo eliminar el caso de prueba.") {
            testCaseDao.deleteById(testCaseId)
            AppLogger.event(AppLogger.Event.TEST_DELETED, "id=$testCaseId")
        }
    }

    suspend fun resetResults(projectId: Long): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudieron reiniciar los resultados.") {
            testCaseDao.resetResults(projectId, System.currentTimeMillis())
        }
    }
}
