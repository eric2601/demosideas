package com.qareport.generator.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.qareport.generator.domain.model.ReportFormat
import java.io.File

/**
 * Compartir y abrir archivos mediante intents estándar de Android.
 *
 * Nunca se asume que una aplicación concreta (WhatsApp, Gmail, Drive…) esté instalada:
 * siempre se usa el Sharesheet del sistema y se comprueba que exista alguna actividad
 * capaz de atender el intent antes de lanzarlo.
 */
object ShareUtils {

    fun uriFor(context: Context, file: File) =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * Abre el Sharesheet del sistema. Devuelve false si no hay ninguna aplicación
     * capaz de recibir el archivo, para que la UI muestre un mensaje en vez de fallar.
     */
    fun shareFile(context: Context, file: File, mimeType: String, title: String): Boolean {
        if (!file.exists()) {
            AppLogger.warn("Se intentó compartir un archivo inexistente")
            return false
        }
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            AppLogger.event(AppLogger.Event.SHARE_REQUESTED, "type=$mimeType")
            context.startActivity(chooser)
            true
        } catch (error: ActivityNotFoundException) {
            AppLogger.warn("No hay aplicaciones disponibles para compartir", error)
            false
        } catch (error: SecurityException) {
            AppLogger.error("Permiso denegado al compartir el archivo", error)
            false
        }
    }

    fun shareReport(context: Context, file: File, format: ReportFormat, title: String): Boolean =
        shareFile(context, file, format.mimeType, title)

    /** Abre el archivo con el visor que el usuario elija. */
    fun openFile(context: Context, file: File, mimeType: String): Boolean {
        if (!file.exists()) return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriFor(context, file), mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Se ofrece un selector para no depender de un visor concreto instalado.
            val chooser = Intent.createChooser(intent, file.name).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (error: ActivityNotFoundException) {
            AppLogger.warn("No hay una aplicación capaz de abrir el archivo", error)
            false
        } catch (error: SecurityException) {
            AppLogger.error("Permiso denegado al abrir el archivo", error)
            false
        }
    }

    /** Comparte un texto plano (por ejemplo un log) sin escribir un archivo. */
    fun shareText(context: Context, text: String, title: String): Boolean = try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(intent, title).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        true
    } catch (error: ActivityNotFoundException) {
        AppLogger.warn("No hay aplicaciones disponibles para compartir texto", error)
        false
    }
}
