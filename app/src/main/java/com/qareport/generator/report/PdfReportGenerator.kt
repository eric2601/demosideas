package com.qareport.generator.report

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.qareport.generator.domain.model.EvidenceType
import com.qareport.generator.domain.model.ProjectBundle
import com.qareport.generator.domain.model.ProjectStats
import com.qareport.generator.domain.model.ReportSettings
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.report.ConclusionGenerator
import com.qareport.generator.domain.stats.StatsCalculator
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.util.TextUtils
import com.qareport.generator.utils.AppLogger
import com.qareport.generator.utils.BitmapLoader
import com.qareport.generator.utils.FileStorage
import java.io.File
import java.io.FileOutputStream

/**
 * Genera el reporte QA en PDF de forma local, sin librerías externas, usando
 * [PdfDocument] de la plataforma.
 *
 * Todo el contenido proviene del [ProjectBundle] recibido: el generador no consulta la
 * base de datos ni calcula datos que no estén registrados.
 */
class PdfReportGenerator(private val fileStorage: FileStorage) {

    /**
     * Escribe el PDF y devuelve el archivo generado.
     * Lanza [java.io.IOException] si la escritura falla; el llamador lo envuelve en un
     * `Outcome` mediante `safeCall`.
     */
    fun generate(bundle: ProjectBundle, settings: ReportSettings): File {
        val stats = StatsCalculator.compute(bundle.testCases, bundle.bugs)
        val document = PdfDocument()
        val writer = PdfPageWriter(document)
        val file = File(fileStorage.reportsDir, buildFileName(bundle))

        try {
            writer.startNewPage()
            drawCover(writer, bundle, stats)

            writer.startNewPage()
            drawExecutiveSummary(writer, stats)
            drawEnvironment(writer, bundle)
            drawTestMatrix(writer, bundle, settings)
            drawFailedDetails(writer, bundle, settings)
            drawBugs(writer, bundle)
            if (settings.includeEvidence) {
                drawEvidence(writer, bundle)
            }
            if (settings.includeLogs) {
                drawLogs(writer, bundle)
            }
            if (settings.includeAutoConclusion) {
                drawConclusion(writer, stats)
            }
            writer.finishCurrentPage()

            FileOutputStream(file).use { output -> document.writeTo(output) }
        } finally {
            // finishPage sobre una página ya cerrada es inofensivo; close siempre debe ocurrir.
            runCatching { writer.finishCurrentPage() }
            document.close()
        }

        return file
    }

    fun buildFileName(bundle: ProjectBundle): String {
        val name = TextUtils.toSafeFileName(bundle.project.name, "QA")
        val version = TextUtils.toSafeFileName(bundle.project.version.ifBlank { "sv" }, "sv")
        return "QA_${name}_${version}_${DateFormats.fileTimestamp()}.pdf"
    }

    // --- portada ---

    private fun drawCover(writer: PdfPageWriter, bundle: ProjectBundle, stats: ProjectStats) {
        val project = bundle.project

        writer.drawFilledRect(
            RectF(0f, 0f, PdfPageWriter.PAGE_WIDTH, 170f),
            PdfPageWriter.COLOR_PRIMARY
        )
        writer.advance(46f)
        writer.drawParagraph("QA TEST REPORT", coverTitlePaint())
        writer.spacer(4f)
        writer.drawParagraph(project.name.ifBlank { "Proyecto sin nombre" }, coverSubtitlePaint())
        writer.advance(50f)

        writer.spacer(20f)
        coverField(writer, "Proyecto", project.name)
        coverField(writer, "Cliente", project.client)
        coverField(writer, "Producto", project.product)
        coverField(writer, "Versión", project.version)
        coverField(writer, "Build", project.buildNumber)
        coverField(writer, "Dispositivo", project.deviceModel.ifBlank { project.deviceType })
        coverField(writer, "Ambiente", project.environment)
        coverField(writer, "Tester", project.tester)
        coverField(writer, "Fecha", DateFormats.formatDate(System.currentTimeMillis()))

        writer.spacer(24f)
        writer.drawHorizontalLine()
        writer.spacer(12f)

        val passRateText = "PASS RATE: ${TextUtils.formatPercent(stats.passRate)}"
        writer.drawParagraph(passRateText, headingPaint())
        writer.drawParagraph(
            "${stats.total} casos registrados · ${stats.executed} ejecutados · ${stats.failed} fallidos",
            mutedPaint()
        )
    }

    private fun coverField(writer: PdfPageWriter, label: String, value: String) {
        val labelPaint = labelPaint()
        val valuePaint = bodyPaint()
        val lineHeight = writer.measureLineHeight(valuePaint)
        writer.ensureSpace(lineHeight)
        val baseline = writer.cursorY - valuePaint.fontMetrics.ascent
        writer.drawSingleLine(label.uppercase(), labelPaint, writer.contentLeft, baseline, 110f)
        writer.drawSingleLine(
            TextUtils.orDash(value),
            valuePaint,
            writer.contentLeft + 120f,
            baseline,
            writer.contentWidth - 120f
        )
        writer.advance(lineHeight)
    }

    // --- resumen ejecutivo ---

    private fun drawExecutiveSummary(writer: PdfPageWriter, stats: ProjectStats) {
        sectionTitle(writer, "RESUMEN EJECUTIVO")

        val cardHeight = 54f
        val gap = 8f
        val cardWidth = (writer.contentWidth - gap * 2) / 3f
        val rows = listOf(
            listOf(
                Triple("TOTAL", stats.total.toString(), PdfPageWriter.COLOR_PRIMARY),
                Triple("PASS", stats.passed.toString(), PdfPageWriter.COLOR_PASS),
                Triple("FAIL", stats.failed.toString(), PdfPageWriter.COLOR_FAIL)
            ),
            listOf(
                Triple("BLOCKED", stats.blocked.toString(), PdfPageWriter.COLOR_BLOCKED),
                Triple("SKIPPED", stats.skipped.toString(), PdfPageWriter.COLOR_SKIPPED),
                Triple("NOT TESTED", stats.notTested.toString(), PdfPageWriter.COLOR_PENDING)
            )
        )

        rows.forEach { row ->
            writer.ensureSpace(cardHeight + gap)
            row.forEachIndexed { index, (label, value, color) ->
                val left = writer.contentLeft + index * (cardWidth + gap)
                val rect = RectF(left, writer.cursorY, left + cardWidth, writer.cursorY + cardHeight)
                writer.drawFilledRect(rect, PdfPageWriter.COLOR_SURFACE, cornerRadius = 6f)
                writer.drawSingleLine(label, labelPaint(), left + 10f, writer.cursorY + 18f, cardWidth - 20f)
                val numberPaint = numberPaint().apply { this.color = color }
                writer.drawSingleLine(value, numberPaint, left + 10f, writer.cursorY + 44f, cardWidth - 20f)
            }
            writer.advance(cardHeight + gap)
        }

        writer.spacer(6f)
        drawDistributionBar(writer, stats)
        writer.spacer(6f)
        writer.drawParagraph("PASS RATE: ${TextUtils.formatPercent(stats.passRate)}", headingPaint())
        writer.drawParagraph(
            "Avance de ejecución: ${TextUtils.formatPercent(stats.executionRate)} " +
                "(${stats.executed} de ${stats.total})",
            mutedPaint()
        )
        writer.spacer(10f)
    }

    /** Barra apilada con la distribución real de resultados. */
    private fun drawDistributionBar(writer: PdfPageWriter, stats: ProjectStats) {
        if (stats.total <= 0) return
        val barHeight = 16f
        writer.ensureSpace(barHeight + 10f)
        val top = writer.cursorY
        var left = writer.contentLeft

        val segments = listOf(
            stats.passed to PdfPageWriter.COLOR_PASS,
            stats.failed to PdfPageWriter.COLOR_FAIL,
            stats.blocked to PdfPageWriter.COLOR_BLOCKED,
            stats.skipped to PdfPageWriter.COLOR_SKIPPED,
            stats.notTested to PdfPageWriter.COLOR_PENDING
        )
        segments.forEach { (count, color) ->
            if (count <= 0) return@forEach
            val width = writer.contentWidth * count / stats.total
            writer.drawFilledRect(RectF(left, top, left + width, top + barHeight), color)
            left += width
        }
        writer.advance(barHeight + 10f)
    }

    // --- ambiente ---

    private fun drawEnvironment(writer: PdfPageWriter, bundle: ProjectBundle) {
        sectionTitle(writer, "INFORMACIÓN DEL AMBIENTE")
        val project = bundle.project
        val fields = listOf(
            "Dispositivo" to project.deviceType,
            "Modelo" to project.deviceModel,
            "Sistema operativo" to project.operatingSystem,
            "Versión Android" to project.androidVersion,
            "Versión de la aplicación" to project.version,
            "Build" to project.buildNumber,
            "Ambiente" to project.environment,
            "Tester" to project.tester,
            "Fecha de inicio" to DateFormats.formatDate(project.startDate),
            "Fecha de finalización" to DateFormats.formatDate(project.endDate)
        )
        fields.forEach { (label, value) -> coverField(writer, label, value) }

        if (project.description.isNotBlank()) {
            writer.spacer(8f)
            writer.drawParagraph("Descripción", labelPaint())
            writer.drawParagraph(project.description, bodyPaint())
        }
        if (project.notes.isNotBlank()) {
            writer.spacer(8f)
            writer.drawParagraph("Notas", labelPaint())
            writer.drawParagraph(project.notes, bodyPaint())
        }
        writer.spacer(12f)
    }

    // --- matriz de pruebas ---

    private fun drawTestMatrix(writer: PdfPageWriter, bundle: ProjectBundle, settings: ReportSettings) {
        sectionTitle(writer, "MATRIZ DE PRUEBAS")

        val cases = bundle.testCases.filter { case ->
            when (case.status) {
                TestStatus.PASS -> settings.includePassedCases
                TestStatus.FAIL -> settings.includeFailedCases
                else -> true
            }
        }

        if (cases.isEmpty()) {
            writer.drawParagraph("No hay casos de prueba que mostrar con la configuración actual.", mutedPaint())
            writer.spacer(12f)
            return
        }

        val columns = floatArrayOf(60f, 0f, 78f, 78f)
        columns[1] = writer.contentWidth - columns[0] - columns[2] - columns[3]
        drawTableHeader(writer, listOf("ID", "CASO", "RESULTADO", "SEVERIDAD"), columns)

        cases.forEach { case -> drawMatrixRow(writer, case, columns) }
        writer.spacer(12f)
    }

    private fun drawTableHeader(writer: PdfPageWriter, titles: List<String>, columns: FloatArray) {
        val rowHeight = 22f
        writer.ensureSpace(rowHeight)
        writer.drawFilledRect(
            RectF(writer.contentLeft, writer.cursorY, writer.contentRight, writer.cursorY + rowHeight),
            PdfPageWriter.COLOR_PRIMARY
        )
        var left = writer.contentLeft + 6f
        titles.forEachIndexed { index, title ->
            writer.drawSingleLine(
                title,
                tableHeaderPaint(),
                left,
                writer.cursorY + 15f,
                columns[index] - 10f
            )
            left += columns[index]
        }
        writer.advance(rowHeight)
    }

    private fun drawMatrixRow(writer: PdfPageWriter, case: TestCase, columns: FloatArray) {
        val paint = tableCellPaint()
        val titleLines = writer.wrapLines(case.title.ifBlank { "-" }, paint, columns[1] - 10f)
        val lineHeight = writer.measureLineHeight(paint, lineSpacing = 2f)
        val rowHeight = (lineHeight * titleLines.size).coerceAtLeast(20f) + 6f

        writer.ensureSpace(rowHeight)
        val top = writer.cursorY
        writer.drawFilledRect(
            RectF(writer.contentLeft, top, writer.contentRight, top + rowHeight),
            PdfPageWriter.COLOR_SURFACE
        )

        var left = writer.contentLeft + 6f
        val firstBaseline = top + 14f

        writer.drawSingleLine(case.code, tableCellBoldPaint(), left, firstBaseline, columns[0] - 10f)
        left += columns[0]

        titleLines.forEachIndexed { index, line ->
            writer.drawSingleLine(line, paint, left, firstBaseline + index * lineHeight, columns[1] - 10f)
        }
        left += columns[1]

        val statusPaint = tableCellBoldPaint().apply { color = statusColor(case.status) }
        writer.drawSingleLine(case.status.label, statusPaint, left, firstBaseline, columns[2] - 10f)
        left += columns[2]

        writer.drawSingleLine(case.severity?.label ?: "-", paint, left, firstBaseline, columns[3] - 10f)
        writer.advance(rowHeight + 2f)
    }

    // --- detalle de fallos ---

    private fun drawFailedDetails(writer: PdfPageWriter, bundle: ProjectBundle, settings: ReportSettings) {
        val failed = bundle.testCases.filter { it.status == TestStatus.FAIL }
        if (failed.isEmpty()) return

        sectionTitle(writer, "DETALLE DE PRUEBAS FALLIDAS")

        failed.forEach { case ->
            writer.ensureSpace(90f)
            writer.drawParagraph("${case.code} · ${case.title}", subheadingPaint())
            field(writer, "Módulo", case.module)
            field(writer, "Prioridad", case.priority.label)
            field(writer, "Severidad", case.severity?.label ?: "-")
            field(writer, "Resultado esperado", case.expectedResult)
            field(writer, "Resultado actual", case.actualResult)

            val steps = TextUtils.splitSteps(case.steps)
            if (steps.isNotEmpty()) {
                writer.drawParagraph("Pasos", labelPaint())
                steps.forEachIndexed { index, step ->
                    writer.drawParagraph("${index + 1}. $step", bodyPaint(), left = writer.contentLeft + 12f)
                }
            }
            if (case.comments.isNotBlank()) {
                field(writer, "Comentarios", case.comments)
            }

            if (settings.includeEvidence) {
                drawEvidenceFor(writer, bundle, case)
            }
            writer.spacer(10f)
            writer.drawHorizontalLine()
        }
        writer.spacer(6f)
    }

    private fun drawEvidenceFor(writer: PdfPageWriter, bundle: ProjectBundle, case: TestCase) {
        val evidence = bundle.evidenceFor(case.id)
        if (evidence.isEmpty()) return

        writer.spacer(4f)
        writer.drawParagraph("Evidencias", labelPaint())
        evidence.forEach { item ->
            writer.drawParagraph(
                "· ${item.displayName} (${item.type.label}, ${TextUtils.formatBytes(item.sizeBytes)})",
                bodyPaint(),
                left = writer.contentLeft + 12f
            )
            if (item.type.isImage) {
                drawImageEvidence(writer, item.uri)
            }
        }
    }

    private fun drawImageEvidence(writer: PdfPageWriter, path: String) {
        var bitmap: Bitmap? = null
        try {
            bitmap = BitmapLoader.decodeScaled(path)
            if (bitmap == null) {
                writer.drawParagraph(
                    "  (La imagen no está disponible en el dispositivo)",
                    mutedPaint(),
                    left = writer.contentLeft + 12f
                )
                return
            }
            writer.drawBitmap(bitmap, maxWidth = writer.contentWidth - 24f)
        } catch (error: Exception) {
            AppLogger.warn("No se pudo incrustar una evidencia en el PDF", error)
        } finally {
            bitmap?.recycle()
        }
    }

    // --- defectos ---

    private fun drawBugs(writer: PdfPageWriter, bundle: ProjectBundle) {
        if (bundle.bugs.isEmpty()) return
        sectionTitle(writer, "DEFECTOS REGISTRADOS")

        bundle.bugs.forEach { bug ->
            writer.ensureSpace(90f)
            writer.drawParagraph("${bug.code} · ${bug.title}", subheadingPaint())
            field(writer, "Severidad", bug.severity.label)
            field(writer, "Prioridad", bug.priority.label)
            field(writer, "Estado", bug.status.label)
            field(writer, "Versión", bug.version)
            field(writer, "Dispositivo", bug.device)
            field(writer, "Descripción", bug.description)

            val steps = TextUtils.splitSteps(bug.stepsToReproduce)
            if (steps.isNotEmpty()) {
                writer.drawParagraph("Pasos para reproducir", labelPaint())
                steps.forEachIndexed { index, step ->
                    writer.drawParagraph("${index + 1}. $step", bodyPaint(), left = writer.contentLeft + 12f)
                }
            }
            field(writer, "Resultado esperado", bug.expectedResult)
            field(writer, "Resultado actual", bug.actualResult)
            writer.spacer(8f)
            writer.drawHorizontalLine()
        }
        writer.spacer(6f)
    }

    // --- evidencias generales ---

    private fun drawEvidence(writer: PdfPageWriter, bundle: ProjectBundle) {
        if (bundle.evidences.isEmpty()) return
        sectionTitle(writer, "EVIDENCIAS")

        val byCase = bundle.testCases.associateBy { it.id }
        bundle.evidences.forEach { item ->
            val owner = item.testCaseId?.let { byCase[it] }
            val ownerLabel = owner?.let { "${it.code} · ${it.title}" } ?: "Proyecto"
            writer.ensureSpace(40f)
            writer.drawParagraph(item.displayName, subheadingPaint())
            writer.drawParagraph(
                "$ownerLabel · ${item.type.label} · ${TextUtils.formatBytes(item.sizeBytes)} · " +
                    DateFormats.formatDateTime(item.createdAt),
                mutedPaint()
            )
            if (item.note.isNotBlank()) {
                writer.drawParagraph(item.note, bodyPaint())
            }
            if (item.type == EvidenceType.PHOTO || item.type == EvidenceType.SCREENSHOT) {
                drawImageEvidence(writer, item.uri)
            }
            writer.spacer(6f)
        }
        writer.spacer(6f)
    }

    // --- logs ---

    private fun drawLogs(writer: PdfPageWriter, bundle: ProjectBundle) {
        if (bundle.logs.isEmpty()) return
        sectionTitle(writer, "LOGS")

        bundle.logs.forEach { log ->
            writer.ensureSpace(50f)
            writer.drawParagraph(log.title.ifBlank { "Log" }, subheadingPaint())
            writer.drawParagraph(DateFormats.formatDateTime(log.createdAt), mutedPaint())
            writer.spacer(2f)
            val trimmed = TextUtils.ellipsize(log.content, MAX_LOG_CHARS)
            writer.drawParagraph(trimmed, monospacePaint(), lineSpacing = 2f)
            if (log.content.length > MAX_LOG_CHARS) {
                writer.drawParagraph(
                    "(Log truncado: ${log.content.length} caracteres en total. " +
                        "Exporta el TXT para verlo completo.)",
                    mutedPaint()
                )
            }
            writer.spacer(8f)
        }
        writer.spacer(6f)
    }

    // --- conclusión ---

    private fun drawConclusion(writer: PdfPageWriter, stats: ProjectStats) {
        sectionTitle(writer, "CONCLUSIÓN")
        val conclusion = ConclusionGenerator.generate(stats)

        writer.ensureSpace(28f)
        val badgeColor = when (conclusion.recommendation) {
            ConclusionGenerator.Recommendation.APPROVED -> PdfPageWriter.COLOR_PASS
            ConclusionGenerator.Recommendation.APPROVED_WITH_RESERVATIONS -> PdfPageWriter.COLOR_BLOCKED
            ConclusionGenerator.Recommendation.REJECTED -> PdfPageWriter.COLOR_FAIL
            else -> PdfPageWriter.COLOR_PENDING
        }
        val badgeWidth = 190f
        writer.drawFilledRect(
            RectF(writer.contentLeft, writer.cursorY, writer.contentLeft + badgeWidth, writer.cursorY + 22f),
            badgeColor,
            cornerRadius = 4f
        )
        writer.drawSingleLine(
            conclusion.recommendation.label.uppercase(),
            tableHeaderPaint(),
            writer.contentLeft + 10f,
            writer.cursorY + 15f,
            badgeWidth - 20f
        )
        writer.advance(30f)

        conclusion.text.split('\n').forEach { paragraph ->
            writer.drawParagraph(paragraph, bodyPaint())
            writer.spacer(4f)
        }
    }

    // --- utilidades de dibujo ---

    private fun sectionTitle(writer: PdfPageWriter, title: String) {
        writer.ensureSpace(40f)
        writer.drawParagraph(title, headingPaint())
        writer.drawHorizontalLine(PdfPageWriter.COLOR_PRIMARY, thickness = 2f)
        writer.spacer(4f)
    }

    private fun field(writer: PdfPageWriter, label: String, value: String) {
        if (value.isBlank()) return
        writer.drawParagraph("$label: ${value.replace('\n', ' ')}", bodyPaint())
    }

    private fun statusColor(status: TestStatus): Int = when (status) {
        TestStatus.PASS -> PdfPageWriter.COLOR_PASS
        TestStatus.FAIL -> PdfPageWriter.COLOR_FAIL
        TestStatus.BLOCKED -> PdfPageWriter.COLOR_BLOCKED
        TestStatus.SKIPPED -> PdfPageWriter.COLOR_SKIPPED
        TestStatus.NOT_TESTED -> PdfPageWriter.COLOR_PENDING
    }

    @Suppress("unused")
    private fun severityColor(severity: Severity?): Int = when (severity) {
        Severity.BLOCKER, Severity.CRITICAL -> PdfPageWriter.COLOR_FAIL
        Severity.MAJOR -> PdfPageWriter.COLOR_BLOCKED
        Severity.MINOR -> PdfPageWriter.COLOR_PENDING
        null -> PdfPageWriter.COLOR_MUTED
    }

    // --- pinceles ---

    private fun basePaint(size: Float, color: Int, bold: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }

    private fun coverTitlePaint(): TextPaint = basePaint(30f, Color.WHITE, bold = true)

    private fun coverSubtitlePaint(): TextPaint = basePaint(15f, Color.rgb(214, 228, 255))

    private fun headingPaint(): TextPaint = basePaint(14f, PdfPageWriter.COLOR_PRIMARY, bold = true)

    private fun subheadingPaint(): TextPaint = basePaint(11.5f, PdfPageWriter.COLOR_TEXT, bold = true)

    private fun bodyPaint(): TextPaint = basePaint(10f, PdfPageWriter.COLOR_TEXT)

    private fun mutedPaint(): TextPaint = basePaint(9f, PdfPageWriter.COLOR_MUTED)

    private fun labelPaint(): TextPaint = basePaint(8.5f, PdfPageWriter.COLOR_MUTED, bold = true)

    private fun numberPaint(): TextPaint = basePaint(20f, PdfPageWriter.COLOR_TEXT, bold = true)

    private fun tableHeaderPaint(): TextPaint = basePaint(9f, Color.WHITE, bold = true)

    private fun tableCellPaint(): TextPaint = basePaint(9f, PdfPageWriter.COLOR_TEXT)

    private fun tableCellBoldPaint(): TextPaint = basePaint(9f, PdfPageWriter.COLOR_TEXT, bold = true)

    private fun monospacePaint(): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PdfPageWriter.COLOR_TEXT
        textSize = 8.5f
        typeface = Typeface.MONOSPACE
    }

    private companion object {
        /** Los logs muy largos se truncan en el PDF; el TXT exportado los conserva completos. */
        const val MAX_LOG_CHARS = 4000
    }
}
