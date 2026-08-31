package com.qareport.generator.data.repository

import android.net.Uri
import com.qareport.generator.core.Outcome
import com.qareport.generator.core.safeCall
import com.qareport.generator.data.local.dao.EvidenceDao
import com.qareport.generator.data.mapper.toDomain
import com.qareport.generator.data.mapper.toEntity
import com.qareport.generator.domain.model.Evidence
import com.qareport.generator.domain.model.EvidenceType
import com.qareport.generator.utils.AppLogger
import com.qareport.generator.utils.FileStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Evidencias.
 *
 * El archivo seleccionado se COPIA al almacenamiento privado de la aplicación y en Room
 * solo se guarda su ruta y sus metadatos. Copiar (en lugar de conservar la URI original
 * del Storage Access Framework) evita que la evidencia desaparezca del reporte si el
 * usuario borra el archivo original o si se revoca el permiso de lectura.
 */
class EvidenceRepository(
    private val evidenceDao: EvidenceDao,
    private val fileStorage: FileStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    fun observeByProject(projectId: Long): Flow<List<Evidence>> =
        evidenceDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeByTestCase(testCaseId: Long): Flow<List<Evidence>> =
        evidenceDao.observeByTestCase(testCaseId).map { list -> list.map { it.toDomain() } }

    fun observeByBug(bugId: Long): Flow<List<Evidence>> =
        evidenceDao.observeByBug(bugId).map { list -> list.map { it.toDomain() } }

    suspend fun getByProject(projectId: Long): List<Evidence> = withContext(dispatcher) {
        evidenceDao.getByProject(projectId).map { it.toDomain() }
    }

    /** Adjunta un archivo elegido con el Storage Access Framework o desde la galería. */
    suspend fun attachFromUri(
        source: Uri,
        projectId: Long,
        testCaseId: Long? = null,
        bugId: Long? = null,
        note: String = ""
    ): Outcome<Long> = withContext(dispatcher) {
        safeCall("No se pudo adjuntar la evidencia.") {
            val displayName = fileStorage.queryDisplayName(source)
            val mimeType = fileStorage.queryMimeType(source)
            val copied = fileStorage.copyEvidence(source, projectId, displayName)
                ?: throw IllegalStateException("El archivo no se pudo copiar o supera el tamaño máximo permitido.")

            insertRecord(
                file = copied,
                projectId = projectId,
                testCaseId = testCaseId,
                bugId = bugId,
                displayName = displayName,
                type = EvidenceType.fromMimeType(mimeType, displayName),
                mimeType = mimeType,
                note = note
            )
        }
    }

    /** Registra una foto recién tomada con la cámara. */
    suspend fun attachCameraPhoto(
        cameraFile: File,
        projectId: Long,
        testCaseId: Long? = null,
        bugId: Long? = null,
        note: String = ""
    ): Outcome<Long> = withContext(dispatcher) {
        safeCall("No se pudo guardar la fotografía.") {
            val stored = fileStorage.promoteCameraFile(cameraFile, projectId)
                ?: throw IllegalStateException("La fotografía no se pudo guardar.")

            insertRecord(
                file = stored,
                projectId = projectId,
                testCaseId = testCaseId,
                bugId = bugId,
                displayName = stored.name,
                type = EvidenceType.PHOTO,
                mimeType = "image/jpeg",
                note = note
            )
        }
    }

    /** Guarda un texto (por ejemplo un log pegado) como evidencia TXT. */
    suspend fun attachText(
        content: String,
        fileName: String,
        projectId: Long,
        testCaseId: Long? = null,
        bugId: Long? = null
    ): Outcome<Long> = withContext(dispatcher) {
        safeCall("No se pudo guardar el texto como evidencia.") {
            val target = File(fileStorage.evidenceDirFor(projectId), fileName)
            target.writeText(content, Charsets.UTF_8)

            insertRecord(
                file = target,
                projectId = projectId,
                testCaseId = testCaseId,
                bugId = bugId,
                displayName = fileName,
                type = EvidenceType.LOG,
                mimeType = "text/plain",
                note = ""
            )
        }
    }

    suspend fun delete(evidence: Evidence): Outcome<Unit> = withContext(dispatcher) {
        safeCall("No se pudo eliminar la evidencia.") {
            evidenceDao.deleteById(evidence.id)
            fileStorage.delete(evidence.uri)
            AppLogger.event(AppLogger.Event.EVIDENCE_DELETED, "id=${evidence.id}")
        }
    }

    /** Restaura evidencias importadas desde un respaldo JSON (sin volver a copiar archivos). */
    suspend fun restoreAll(evidences: List<Evidence>): Outcome<List<Long>> = withContext(dispatcher) {
        safeCall("No se pudieron restaurar las evidencias.") {
            evidenceDao.insertAll(evidences.map { it.toEntity() })
        }
    }

    private suspend fun insertRecord(
        file: File,
        projectId: Long,
        testCaseId: Long?,
        bugId: Long?,
        displayName: String,
        type: EvidenceType,
        mimeType: String,
        note: String
    ): Long {
        val evidence = Evidence(
            projectId = projectId,
            testCaseId = testCaseId,
            bugId = bugId,
            displayName = displayName,
            uri = file.absolutePath,
            type = type,
            mimeType = mimeType,
            sizeBytes = file.length(),
            note = note,
            createdAt = System.currentTimeMillis()
        )
        val id = evidenceDao.insert(evidence.toEntity())
        AppLogger.event(AppLogger.Event.EVIDENCE_ADDED, "id=$id type=${type.key} bytes=${file.length()}")
        return id
    }
}
