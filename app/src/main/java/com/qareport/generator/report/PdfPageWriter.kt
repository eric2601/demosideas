package com.qareport.generator.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils as AndroidTextUtils

/**
 * Envoltorio sobre [PdfDocument] que gestiona la paginación.
 *
 * Todo el dibujo pasa por aquí: el escritor conoce la posición vertical actual y abre
 * una página nueva automáticamente cuando el contenido ya no cabe, de modo que el
 * generador del reporte solo describe el contenido y nunca calcula páginas a mano.
 *
 * Tamaño de página: A4 en puntos PostScript (595 x 842), que es la unidad nativa de
 * [PdfDocument].
 */
class PdfPageWriter(private val document: PdfDocument) {

    private var currentPage: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var pageNumber = 0

    /** Posición vertical del siguiente elemento a dibujar. */
    var cursorY: Float = MARGIN_TOP
        private set

    val contentWidth: Float get() = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

    val contentLeft: Float get() = MARGIN_LEFT

    val contentRight: Float get() = PAGE_WIDTH - MARGIN_RIGHT

    val pagesWritten: Int get() = pageNumber

    // --- ciclo de vida de páginas ---

    fun startNewPage() {
        finishCurrentPage()
        pageNumber++
        val pageInfo = PdfDocument.PageInfo
            .Builder(PAGE_WIDTH.toInt(), PAGE_HEIGHT.toInt(), pageNumber)
            .create()
        val page = document.startPage(pageInfo)
        currentPage = page
        canvas = page.canvas
        cursorY = MARGIN_TOP
        drawPageFooter()
    }

    fun finishCurrentPage() {
        val page = currentPage ?: return
        document.finishPage(page)
        currentPage = null
        canvas = null
    }

    private fun requireCanvas(): Canvas {
        if (canvas == null) startNewPage()
        return canvas ?: error("No hay página activa en el documento PDF")
    }

    /** Garantiza que quedan [height] puntos disponibles; si no, abre una página nueva. */
    fun ensureSpace(height: Float) {
        requireCanvas()
        if (cursorY + height > PAGE_HEIGHT - MARGIN_BOTTOM) {
            startNewPage()
        }
    }

    fun advance(height: Float) {
        cursorY += height
    }

    fun spacer(height: Float = 12f) {
        ensureSpace(height)
        cursorY += height
    }

    // --- texto ---

    /**
     * Dibuja un bloque de texto con ajuste de línea, repartiéndolo entre páginas si es
     * necesario. Devuelve la altura total consumida en la página actual.
     */
    fun drawParagraph(
        text: String,
        paint: TextPaint,
        left: Float = contentLeft,
        width: Float = contentWidth,
        lineSpacing: Float = 4f
    ) {
        if (text.isBlank()) return
        val lines = wrapLines(text, paint, width)
        val lineHeight = paint.fontMetrics.let { it.descent - it.ascent } + lineSpacing
        lines.forEach { line ->
            ensureSpace(lineHeight)
            requireCanvas().drawText(line, left, cursorY - paint.fontMetrics.ascent, paint)
            cursorY += lineHeight
        }
    }

    /** Dibuja una sola línea recortada con puntos suspensivos si no cabe en [width]. */
    fun drawSingleLine(
        text: String,
        paint: TextPaint,
        left: Float,
        baselineOffsetY: Float,
        width: Float
    ) {
        val ellipsized = AndroidTextUtils
            .ellipsize(text, paint, width, AndroidTextUtils.TruncateAt.END)
            .toString()
        requireCanvas().drawText(ellipsized, left, baselineOffsetY, paint)
    }

    fun measureLineHeight(paint: Paint, lineSpacing: Float = 4f): Float =
        paint.fontMetrics.let { it.descent - it.ascent } + lineSpacing

    /** Reparte un texto en líneas usando el mismo algoritmo de salto que Android. */
    fun wrapLines(text: String, paint: TextPaint, width: Float): List<String> {
        val safeWidth = width.coerceAtLeast(1f).toInt()
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .build()
        return (0 until layout.lineCount).map { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd('\n')
        }
    }

    // --- formas ---

    fun drawFilledRect(rect: RectF, color: Int, cornerRadius: Float = 0f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        if (cornerRadius > 0f) {
            requireCanvas().drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        } else {
            requireCanvas().drawRect(rect, paint)
        }
    }

    fun drawHorizontalLine(color: Int = COLOR_DIVIDER, thickness: Float = 1f) {
        ensureSpace(thickness + 6f)
        val paint = Paint().apply {
            this.color = color
            strokeWidth = thickness
        }
        requireCanvas().drawLine(contentLeft, cursorY, contentRight, cursorY, paint)
        cursorY += thickness + 6f
    }

    /** Dibuja un bitmap ajustado al ancho disponible, respetando su relación de aspecto. */
    fun drawBitmap(bitmap: Bitmap, maxWidth: Float = contentWidth, maxHeight: Float = 260f) {
        val ratio = bitmap.height.toFloat() / bitmap.width.toFloat()
        var targetWidth = minOf(maxWidth, bitmap.width.toFloat())
        var targetHeight = targetWidth * ratio
        if (targetHeight > maxHeight) {
            targetHeight = maxHeight
            targetWidth = targetHeight / ratio
        }
        ensureSpace(targetHeight + 8f)
        val destination = RectF(contentLeft, cursorY, contentLeft + targetWidth, cursorY + targetHeight)
        requireCanvas().drawBitmap(bitmap, Rect(0, 0, bitmap.width, bitmap.height), destination, null)
        cursorY += targetHeight + 8f
    }

    private fun drawPageFooter() {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 8f
        }
        val footerY = PAGE_HEIGHT - MARGIN_BOTTOM + 18f
        canvas?.drawText("Página $pageNumber", contentLeft, footerY, paint)
        val label = "QA Report Generator"
        val labelWidth = paint.measureText(label)
        canvas?.drawText(label, contentRight - labelWidth, footerY, paint)
    }

    companion object {
        const val PAGE_WIDTH = 595f
        const val PAGE_HEIGHT = 842f
        const val MARGIN_LEFT = 40f
        const val MARGIN_RIGHT = 40f
        const val MARGIN_TOP = 48f
        const val MARGIN_BOTTOM = 56f

        val COLOR_PRIMARY = Color.rgb(11, 61, 145)
        val COLOR_TEXT = Color.rgb(24, 28, 34)
        val COLOR_MUTED = Color.rgb(110, 118, 128)
        val COLOR_DIVIDER = Color.rgb(214, 220, 228)
        val COLOR_SURFACE = Color.rgb(243, 246, 250)
        val COLOR_PASS = Color.rgb(21, 128, 61)
        val COLOR_FAIL = Color.rgb(185, 28, 28)
        val COLOR_BLOCKED = Color.rgb(180, 83, 9)
        val COLOR_SKIPPED = Color.rgb(109, 40, 217)
        val COLOR_PENDING = Color.rgb(100, 116, 139)
    }
}
