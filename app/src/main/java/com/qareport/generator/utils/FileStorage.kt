package com.qareport.generator.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.util.TextUtils
import java.io.File
import java.io.IOException

/**
 * Gestión del almacenamiento privado de la aplicación.
 *
 * Todo (reportes, exportaciones y evidencias) vive dentro del almacenamiento interno
 * de la app, por lo que no se requiere ningún permiso de almacenamiento y los datos
 * del proyecto no quedan expuestos a otras aplicaciones. Para compartir un archivo se
 * genera una URI temporal mediante [FileProvider].
 */
class FileStorage(private val context: Context) {

    val reportsDir: File get() = ensureDir(File(context.filesDir, DIR_REPORTS))

    val exportsDir: File get() = ensureDir(File(context.filesDir, DIR_EXPORTS))

    val evidenceDir: File get() = ensureDir(File(context.filesDir, DIR_EVIDENCE))

    val cameraDir: File get() = ensureDir(File(context.cacheDir, DIR_CAMERA))

    fun evidenceDirFor(projectId: Long): File = ensureDir(File(evidenceDir, projectId.toString()))

    /** Archivo temporal donde la cámara escribirá la foto. */
    fun createCameraFile(): File =
        File(cameraDir, "foto_${DateFormats.fileTimestamp()}.jpg")

    /** URI compartible con otras aplicaciones (cámara, Sharesheet, visores). */
    fun shareUriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Copia el contenido de [source] al almacenamiento privado de evidencias.
     * Devuelve null si el archivo no se puede leer o supera [MAX_EVIDENCE_BYTES].
     */
    fun copyEvidence(source: Uri, projectId: Long, displayName: String): File? {
        val safeName = TextUtils.toSafeFileName(displayName, fallback = "evidencia")
        val target = File(evidenceDirFor(projectId), "${DateFormats.fileTimestamp()}_$safeName")
        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    val copied = input.copyTo(output)
                    if (copied > MAX_EVIDENCE_BYTES) {
                        output.close()
                        target.delete()
                        AppLogger.warn("Evidencia descartada por exceder el tamaño máximo")
                        return null
                    }
                }
            } ?: return null
            target
        } catch (error: IOException) {
            AppLogger.error("No se pudo copiar la evidencia", error)
            target.delete()
            null
        } catch (error: SecurityException) {
            AppLogger.error("Sin permiso para leer la evidencia seleccionada", error)
            null
        }
    }

    /** Mueve una foto recién capturada desde la caché al almacenamiento de evidencias. */
    fun promoteCameraFile(cameraFile: File, projectId: Long): File? = try {
        val target = File(evidenceDirFor(projectId), cameraFile.name)
        cameraFile.copyTo(target, overwrite = true)
        cameraFile.delete()
        target
    } catch (error: IOException) {
        AppLogger.error("No se pudo guardar la foto capturada", error)
        null
    }

    /** Nombre visible de un documento seleccionado con el Storage Access Framework. */
    fun queryDisplayName(uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "archivo" }
        return try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
                ?: fallback
        } catch (error: Exception) {
            AppLogger.warn("No se pudo leer el nombre del archivo seleccionado", error)
            fallback
        }
    }

    fun queryMimeType(uri: Uri): String =
        runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()

    /** Escribe texto en un archivo del directorio de exportaciones. */
    fun writeExport(fileName: String, content: String, withBom: Boolean = false): File? = try {
        val target = File(exportsDir, fileName)
        target.outputStream().use { output ->
            if (withBom) output.write(BOM_UTF8)
            output.write(content.toByteArray(Charsets.UTF_8))
        }
        target
    } catch (error: IOException) {
        AppLogger.error("No se pudo escribir el archivo de exportación", error)
        null
    }

    fun readText(file: File): String? = try {
        if (file.exists()) file.readText(Charsets.UTF_8) else null
    } catch (error: IOException) {
        AppLogger.error("No se pudo leer el archivo", error)
        null
    }

    fun readTextFrom(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (error: Exception) {
        AppLogger.error("No se pudo leer el archivo seleccionado", error)
        null
    }

    fun delete(path: String): Boolean = try {
        val file = File(path)
        !file.exists() || file.delete()
    } catch (error: SecurityException) {
        AppLogger.warn("No se pudo eliminar el archivo", error)
        false
    }

    /** Limpia las fotos temporales que quedaron en caché por capturas canceladas. */
    fun clearCameraCache() {
        runCatching { cameraDir.listFiles()?.forEach { it.delete() } }
            .onFailure { AppLogger.warn("No se pudo limpiar la caché de cámara", it) }
    }

    private fun ensureDir(dir: File): File {
        if (!dir.exists() && !dir.mkdirs()) {
            AppLogger.warn("No se pudo crear el directorio ${dir.name}")
        }
        return dir
    }

    companion object {
        private const val DIR_REPORTS = "reports"
        private const val DIR_EXPORTS = "exports"
        private const val DIR_EVIDENCE = "evidence"
        private const val DIR_CAMERA = "camera"

        /** Límite por evidencia: 25 MB. Evita llenar el almacenamiento interno. */
        const val MAX_EVIDENCE_BYTES = 25L * 1024L * 1024L

        private val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
    }
}
