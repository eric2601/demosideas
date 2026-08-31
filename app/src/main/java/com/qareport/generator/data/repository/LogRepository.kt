package com.qareport.generator.data.repository

import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.local.dao.LogEntryDao
import com.qareport.generator.data.mapper.toDomain
import com.qareport.generator.data.mapper.toEntity
import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LogRepository(
    private val logEntryDao: LogEntryDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeByProject(projectId: Long): Flow<List<LogEntry>> =
        logEntryDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    suspend fun getByProject(projectId: Long): List<LogEntry> = withContext(dispatcher) {
        logEntryDao.getByProject(projectId).map { it.toDomain() }
    }

    suspend fun getById(logId: Long): LogEntry? = withContext(dispatcher) {
        logEntryDao.getById(logId)?.toDomain()
    }

    suspend fun save(entry: LogEntry): Outcome<Long> = withContext(dispatcher) {
        safeCall("No se pudo guardar el log.") {
            val now = System.currentTimeMillis()
            val id = if (entry.id == 0L) {
                logEntryDao.insert(entry.copy(createdAt = now).toEntity())
            } else {
                logEntryDao.update(entry.toEntity())
                entry.id
            }
            AppLogger.event(AppLogger.Event.LOG_SAVED, "id=$id chars=${entry.content.length}")
            id
        }
    }

    suspend fun saveAll(entries: List<LogEntry>): Outcome<List<Long>> = withContext(dispatcher) {
        safeCall("No se pudieron guardar los logs.") {
            logEntryDao.insertAll(entries.map { it.toEntity() })
        }
    }

    suspend fun delete(logId: Long): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo eliminar el log.") {
            logEntryDao.deleteById(logId)
        }
    }
}
