package com.qareport.generator.data.repository

import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.local.dao.ReportDao
import com.qareport.generator.data.mapper.toDomain
import com.qareport.generator.data.mapper.toEntity
import com.qareport.generator.domain.model.Report
import com.qareport.generator.domain.util.TextUtils
import com.qareport.generator.utils.AppLogger
import com.qareport.generator.utils.FileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/** Historial de reportes y exportaciones generados. */
class ReportRepository(
    private val reportDao: ReportDao,
    private val fileStorage: FileStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeAll(): Flow<List<Report>> =
        reportDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeByProject(projectId: Long): Flow<List<Report>> =
        reportDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(reportId: Long): Report? = withContext(dispatcher) {
        reportDao.getById(reportId)?.toDomain()
    }

    suspend fun register(report: Report): Outcome<Long> = withContext(dispatcher) {
        safeCall("No se pudo registrar el reporte.") {
            reportDao.insert(report.toEntity())
        }
    }

    suspend fun delete(report: Report): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo eliminar el reporte.") {
            reportDao.deleteById(report.id)
            // El registro se elimina aunque el archivo ya no exista en disco.
            if (!fileStorage.delete(report.filePath)) {
                AppLogger.warn("El archivo del reporte no pudo eliminarse del almacenamiento")
            }
        }
    }

    /**
     * Renombra el archivo en disco y su registro. Si el archivo físico no se puede
     * renombrar, no se toca la base de datos para no dejar registros huérfanos.
     */
    suspend fun rename(report: Report, newName: String): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo renombrar el reporte.") {
            val extension = ".${report.format.extension}"
            val safeName = TextUtils.toSafeFileName(newName.removeSuffix(extension), "reporte") + extension
            val current = File(report.filePath)
            val target = File(current.parentFile, safeName)

            if (target.exists()) {
                throw IllegalStateException("Ya existe un archivo con el nombre $safeName.")
            }
            if (!current.exists() || !current.renameTo(target)) {
                throw IllegalStateException("El archivo del reporte no está disponible.")
            }
            reportDao.rename(report.id, safeName, target.absolutePath)
            AppLogger.debug("Reporte renombrado id=${report.id}")
        }
    }

    /** Comprueba que el archivo referenciado siga existiendo en disco. */
    fun fileFor(report: Report): File? {
        val file = File(report.filePath)
        return if (file.exists()) file else null
    }
}
