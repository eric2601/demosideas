package com.qareport.generator.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Carga de imágenes para el PDF con submuestreo.
 *
 * Las fotos de una terminal moderna pueden superar los 4000 px de ancho; decodificarlas
 * a tamaño completo agotaría la memoria de la aplicación. Aquí se calcula un
 * `inSampleSize` para decodificar únicamente la resolución necesaria y se corrige la
 * orientación EXIF, que de otro modo mostraría las fotos giradas en el reporte.
 */
object BitmapLoader {

    /** Ancho máximo útil en el PDF (A4 menos márgenes) con margen de holgura. */
    const val MAX_WIDTH_PX = 1200

    fun decodeScaled(path: String, maxWidth: Int = MAX_WIDTH_PX, maxHeight: Int = MAX_WIDTH_PX): Bitmap? {
        val file = File(path)
        if (!file.exists() || !file.canRead()) return null

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
            applyExifRotation(path, bitmap)
        } catch (error: OutOfMemoryError) {
            AppLogger.error("Sin memoria al decodificar la imagen de evidencia", null)
            null
        } catch (error: Exception) {
            AppLogger.warn("No se pudo decodificar la imagen de evidencia", error)
            null
        }
    }

    fun calculateSampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= maxWidth && currentHeight / 2 >= maxHeight) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun applyExifRotation(path: String, bitmap: Bitmap): Bitmap {
        val degrees = try {
            when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (error: Exception) {
            AppLogger.warn("No se pudo leer la orientación EXIF", error)
            0f
        }
        if (degrees == 0f) return bitmap

        return try {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } catch (error: OutOfMemoryError) {
            bitmap
        }
    }
}
