package com.gymvoice.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportRepository(
    private val dao: WorkoutLogDao,
    private val context: Context,
) {
    sealed class ExportResult {
        data class Success(val uri: Uri) : ExportResult()

        data class Failure(val message: String) : ExportResult()
    }

    suspend fun export(
        format: ExportFormat,
        fromMs: Long,
        toMs: Long,
    ): ExportResult =
        withContext(Dispatchers.IO) {
            try {
                val logs = dao.getLogsInRange(fromMs, toMs)
                if (logs.isEmpty()) return@withContext ExportResult.Failure("No logs in selected range")
                val (bytes, ext, mime) =
                    when (format) {
                        ExportFormat.XLSX ->
                            Triple(
                                buildXlsx(logs),
                                "xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            )
                        ExportFormat.PDF ->
                            Triple(
                                buildPdf(logs, fromMs, toMs),
                                "pdf",
                                "application/pdf",
                            )
                    }
                val uri = saveToDownloads(bytes, fileName(ext, fromMs, toMs), mime)
                ExportResult.Success(uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExportResult.Failure(e.message ?: "Export failed")
            }
        }

    private fun saveToDownloads(
        bytes: ByteArray,
        filename: String,
        mimeType: String,
    ): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, filename)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val resolver = context.contentResolver
            val uri =
                resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore insert returned null")
            (resolver.openOutputStream(uri) ?: error("openOutputStream returned null for $uri"))
                .use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            // API 26-28: requires WRITE_EXTERNAL_STORAGE granted at runtime.
            // The manifest declares it with maxSdkVersion="28". If not yet granted,
            // the SecurityException is caught in export() and surfaced as ExportResult.Failure.
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.mkdirs() && !dir.exists()) error("Cannot create Downloads directory: $dir")
            val file = File(dir, filename)
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, "com.gymvoice.fileprovider", file)
        }
    }

    companion object {
        fun fileName(
            ext: String,
            fromMs: Long,
            toMs: Long,
        ): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return if (fromMs == 0L && toMs == Long.MAX_VALUE) {
                "gymvoice_log_all_time.$ext"
            } else {
                "gymvoice_log_${fmt.format(Date(fromMs))}_to_${fmt.format(Date(toMs))}.$ext"
            }
        }

        fun buildXlsx(logs: List<WorkoutLog>): ByteArray {
            val out = ByteArrayOutputStream()
            XSSFWorkbook().use { workbook ->
                val sheet = workbook.createSheet("Training Log")

                val boldFont = workbook.createFont().apply { bold = true }
                val headerStyle =
                    workbook.createCellStyle().apply {
                        setFont(boldFont)
                        fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                        fillPattern = FillPatternType.SOLID_FOREGROUND
                    }

                val headers = listOf("Date", "Exercise", "Set", "Reps", "Weight", "Unit", "Rest (s)")
                val headerRow = sheet.createRow(0)
                headers.forEachIndexed { i, title ->
                    headerRow.createCell(i).apply {
                        setCellValue(title)
                        cellStyle = headerStyle
                    }
                }

                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                logs.forEachIndexed { index, log ->
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(fmt.format(Date(log.timestamp)))
                    row.createCell(1).setCellValue(log.exerciseName)
                    row.createCell(2).setCellValue((log.setNumber ?: 0).toDouble())
                    row.createCell(3).setCellValue((log.reps ?: 0).toDouble())
                    row.createCell(4).setCellValue((log.weight ?: 0f).toDouble())
                    row.createCell(5).setCellValue(log.unit)
                    row.createCell(6).setCellValue((log.restSeconds ?: 0).toDouble())
                }

                headers.indices.forEach { sheet.autoSizeColumn(it) }
                workbook.write(out)
            }
            return out.toByteArray()
        }

        fun buildPdf(
            logs: List<WorkoutLog>,
            fromMs: Long,
            toMs: Long,
        ): ByteArray {
            val document = android.graphics.pdf.PdfDocument()
            val out = ByteArrayOutputStream()
            try {
                val state = PdfState(document)
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val title =
                    if (fromMs == 0L && toMs == Long.MAX_VALUE) {
                        "GymVoice Training Log — All Time"
                    } else {
                        "GymVoice Training Log — ${fmt.format(Date(fromMs))} to ${fmt.format(Date(toMs))}"
                    }
                state.writeLine(title, state.margin, state.titlePaint)
                state.y += state.lineHeight / 2
                renderGroups(state, groupByDateAndExercise(logs))
                state.finishPage()
                document.writeTo(out)
            } finally {
                document.close()
            }
            return out.toByteArray()
        }

        private fun renderGroups(
            state: PdfState,
            groups: Map<String, Map<String, List<WorkoutLog>>>,
        ) {
            for ((date, byExercise) in groups) {
                state.y += state.lineHeight / 2
                state.writeLine(date, state.margin, state.labelPaint)
                for ((exercise, sets) in byExercise) {
                    state.writeLine("  $exercise", state.margin + 8f, state.bodyPaint)
                    state.writeLine("  Set  Reps  Weight  Unit  Rest(s)", state.margin + 16f, state.columnPaint)
                    for (log in sets) {
                        state.writeLine(formatLogRow(log), state.margin + 16f, state.bodyPaint)
                    }
                    state.y += 4f
                }
                state.y += 8f
            }
        }

        private fun formatLogRow(log: WorkoutLog): String =
            "  ${log.setNumber ?: "—"}     ${log.reps ?: "—"}     " +
                "${log.weight ?: "—"}  ${log.unit}     ${log.restSeconds ?: "—"}"

        private class PdfState(private val doc: android.graphics.pdf.PdfDocument) {
            val margin = 40f
            val lineHeight = 16f
            private val pageWidth = 595
            private val pageHeight = 842
            private val bottomLimit = pageHeight - 40f
            private var pageNum = 1
            private var page =
                doc.startPage(
                    android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create(),
                )
            private var canvas = page.canvas
            var y = margin

            val titlePaint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 14f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
            val labelPaint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 12f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
            val bodyPaint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = 11f
                    isAntiAlias = true
                }
            val columnPaint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 10f
                    isAntiAlias = true
                }
            private val footerPaint =
                android.graphics.Paint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 9f
                    isAntiAlias = true
                }

            fun finishPage() {
                canvas.drawText("$pageNum", (pageWidth / 2).toFloat(), bottomLimit + 20f, footerPaint)
                doc.finishPage(page)
            }

            private fun newPage() {
                finishPage()
                pageNum++
                page =
                    doc.startPage(
                        android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create(),
                    )
                canvas = page.canvas
                y = margin
            }

            fun writeLine(
                text: String,
                x: Float,
                paint: android.graphics.Paint,
            ) {
                if (y + lineHeight > bottomLimit) newPage()
                canvas.drawText(text, x, y, paint)
                y += lineHeight
            }
        }

        fun groupByDateAndExercise(logs: List<WorkoutLog>): Map<String, Map<String, List<WorkoutLog>>> {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return logs
                .groupBy { fmt.format(Date(it.timestamp)) }
                .toSortedMap()
                .mapValues { (_, v) -> v.groupBy { it.exerciseName }.toSortedMap() }
        }
    }
}
