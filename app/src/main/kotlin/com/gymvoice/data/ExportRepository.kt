package com.gymvoice.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
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

    suspend fun export(format: ExportFormat, fromMs: Long, toMs: Long): ExportResult {
        return try {
            val logs = dao.getLogsInRange(fromMs, toMs)
            if (logs.isEmpty()) return ExportResult.Failure("No logs in selected range")
            val (bytes, ext, mime) = when (format) {
                ExportFormat.XLSX -> Triple(
                    buildXlsx(logs),
                    "xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                )
                ExportFormat.PDF -> Triple(
                    buildPdf(logs, fromMs, toMs),
                    "pdf",
                    "application/pdf",
                )
            }
            val uri = saveToDownloads(bytes, fileName(ext, fromMs, toMs), mime)
            ExportResult.Success(uri)
        } catch (e: Exception) {
            ExportResult.Failure(e.message ?: "Export failed")
        }
    }

    private fun saveToDownloads(bytes: ByteArray, filename: String, mimeType: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert returned null")
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
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
            dir.mkdirs()
            val file = File(dir, filename)
            file.writeBytes(bytes)
            FileProvider.getUriForFile(context, "com.gymvoice.fileprovider", file)
        }
    }

    companion object {
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        fun fileName(ext: String, fromMs: Long, toMs: Long): String =
            if (fromMs == 0L && toMs == Long.MAX_VALUE) "gymvoice_log_all_time.$ext"
            else "gymvoice_log_${dateFormatter.format(Date(fromMs))}_to_${dateFormatter.format(Date(toMs))}.$ext"

        fun buildXlsx(logs: List<WorkoutLog>): ByteArray {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Training Log")

            val boldFont = workbook.createFont().apply { bold = true }
            val headerStyle = workbook.createCellStyle().apply {
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

            val out = ByteArrayOutputStream()
            workbook.write(out)
            workbook.close()
            return out.toByteArray()
        }

        fun buildPdf(logs: List<WorkoutLog>, fromMs: Long, toMs: Long): ByteArray {
            // Implemented in Task 6
            throw NotImplementedError("buildPdf not yet implemented")
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
