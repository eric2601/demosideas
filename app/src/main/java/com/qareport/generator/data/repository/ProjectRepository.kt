package com.qareport.generator.data.repository

import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.local.dao.ProjectDao
import com.qareport.generator.data.local.dao.TestCaseDao
import com.qareport.generator.data.mapper.toDomain
import com.qareport.generator.data.mapper.toEntity
import com.qareport.generator.domain.model.GlobalStats
import com.qareport.generator.domain.model.Project
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Acceso a proyectos. Es la única capa que conoce Room; los ViewModels solo ven
 * modelos de dominio y [Outcome].
 */
class ProjectRepository(
    private val projectDao: ProjectDao,
    private val testCaseDao: TestCaseDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeAll(): Flow<List<Project>> =
        projectDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeRecent(limit: Int = 5): Flow<List<Project>> =
        projectDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeById(projectId: Long): Flow<Project?> =
        projectDao.observeById(projectId).map { it?.toDomain() }

    /** Contadores del Dashboard principal, combinando proyectos y casos. */
    fun observeGlobalStats(): Flow<GlobalStats> =
        combine(projectDao.observeCount(), testCaseDao.observeGlobalCounts()) { projects, counts ->
            GlobalStats(
                projects = projects,
                executedTests = counts?.executed ?: 0,
                passed = counts?.passed ?: 0,
                failed = counts?.failed ?: 0
            )
        }

    suspend fun getById(projectId: Long): Project? = withContext(dispatcher) {
        projectDao.getById(projectId)?.toDomain()
    }

    suspend fun create(project: Project): Outcome<Long> = withContext(dispatcher) {
        safeCall("No se pudo crear el proyecto.") {
            val now = System.currentTimeMillis()
            val id = projectDao.insert(project.copy(createdAt = now, updatedAt = now).toEntity())
            AppLogger.event(AppLogger.Event.PROJECT_CREATED, "id=$id")
            id
        }
    }

    suspend fun update(project: Project): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo guardar el proyecto.") {
            projectDao.update(project.copy(updatedAt = System.currentTimeMillis()).toEntity())
            AppLogger.event(AppLogger.Event.PROJECT_UPDATED, "id=${project.id}")
        }
    }

    suspend fun delete(projectId: Long): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo eliminar el proyecto.") {
            projectDao.deleteById(projectId)
            AppLogger.event(AppLogger.Event.PROJECT_DELETED, "id=$projectId")
        }
    }

    suspend fun setFinalized(projectId: Long, finalized: Boolean): Outcome<Unit> =
        withContext(dispatcher) {
            safeCall("No se pudo actualizar el estado del proyecto.") {
                projectDao.setFinalized(projectId, finalized, System.currentTimeMillis())
                AppLogger.event(AppLogger.Event.PROJECT_FINALIZED, "id=$projectId finalized=$finalized")
            }
        }

    suspend fun touch(projectId: Long) = withContext(dispatcher) {
        runCatching { projectDao.touch(projectId, System.currentTimeMillis()) }
            .onFailure { AppLogger.warn("No se pudo actualizar la fecha del proyecto", it) }
    }
}
