package com.qareport.generator.data.repository

import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.local.dao.BugDao
import com.qareport.generator.data.mapper.toDomain
import com.qareport.generator.data.mapper.toEntity
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.stats.StatsCalculator
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BugRepository(
    private val bugDao: BugDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeByProject(projectId: Long): Flow<List<Bug>> =
        bugDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeById(bugId: Long): Flow<Bug?> = bugDao.observeById(bugId).map { it?.toDomain() }

    suspend fun getByProject(projectId: Long): List<Bug> = withContext(dispatcher) {
        bugDao.getByProject(projectId).map { it.toDomain() }
    }

    suspend fun getById(bugId: Long): Bug? = withContext(dispatcher) {
        bugDao.getById(bugId)?.toDomain()
    }

    suspend fun suggestNextCode(projectId: Long): String = withContext(dispatcher) {
        StatsCalculator.nextCode("BUG", bugDao.getCodes(projectId))
    }

    suspend fun create(bug: Bug): Outcome<Long> = withContext(dispatcher) {
        safeCall("No se pudo registrar el defecto.") {
            val now = System.currentTimeMillis()
            val id = bugDao.insert(bug.copy(createdAt = now, updatedAt = now).toEntity())
            AppLogger.event(AppLogger.Event.BUG_CREATED, "id=$id severity=${bug.severity.key}")
            id
        }
    }

    suspend fun createAll(bugs: List<Bug>): Outcome<List<Long>> = withContext(dispatcher) {
        safeCall("No se pudieron registrar los defectos.") {
            bugDao.insertAll(bugs.map { it.toEntity() })
        }
    }

    suspend fun update(bug: Bug): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo guardar el defecto.") {
            bugDao.update(bug.copy(updatedAt = System.currentTimeMillis()).toEntity())
            AppLogger.event(AppLogger.Event.BUG_UPDATED, "id=${bug.id}")
        }
    }

    suspend fun updateStatus(bugId: Long, status: BugStatus): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo cambiar el estado del defecto.") {
            bugDao.updateStatus(bugId, status.key, System.currentTimeMillis())
            AppLogger.event(AppLogger.Event.BUG_UPDATED, "id=$bugId status=${status.key}")
        }
    }

    suspend fun delete(bugId: Long): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo eliminar el defecto.") {
            bugDao.deleteById(bugId)
        }
    }
}
