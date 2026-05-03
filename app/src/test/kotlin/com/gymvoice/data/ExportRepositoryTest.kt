package com.gymvoice.data

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ExportRepositoryTest {
    private val sampleLogs = listOf(
        WorkoutLog(id = 1, sessionId = "s1", exerciseName = "bench press",
            setNumber = 1, reps = 10, weight = 80f, unit = "kg",
            restSeconds = 120, timestamp = 1_000_000L),
        WorkoutLog(id = 2, sessionId = "s1", exerciseName = "bench press",
            setNumber = 2, reps = 8, weight = 85f, unit = "kg",
            restSeconds = 90, timestamp = 2_000_000L),
        WorkoutLog(id = 3, sessionId = "s2", exerciseName = "squat",
            setNumber = 1, reps = 5, weight = 100f, unit = "kg",
            restSeconds = null, timestamp = 3_000_000L),
    )

    @Test
    fun buildXlsx_producesValidSheetWithHeaderAndDataRows() {
        val bytes = ExportRepository.buildXlsx(sampleLogs)

        assertTrue(bytes.isNotEmpty())
        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            assertEquals("Training Log", sheet.sheetName)
            val header = sheet.getRow(0)
            assertEquals("Date", header.getCell(0).stringCellValue)
            assertEquals("Exercise", header.getCell(1).stringCellValue)
            assertEquals("Set", header.getCell(2).stringCellValue)
            assertEquals("Reps", header.getCell(3).stringCellValue)
            assertEquals("Weight", header.getCell(4).stringCellValue)
            assertEquals("Unit", header.getCell(5).stringCellValue)
            assertEquals("Rest (s)", header.getCell(6).stringCellValue)
            assertEquals(3, sheet.lastRowNum)
            assertEquals("bench press", sheet.getRow(1).getCell(1).stringCellValue)
            assertEquals(10.0, sheet.getRow(1).getCell(3).numericCellValue, 0.0)
            assertEquals("squat", sheet.getRow(3).getCell(1).stringCellValue)
        }
    }

    @Test
    fun buildXlsx_emptyLogs_producesOnlyHeaderRow() {
        val bytes = ExportRepository.buildXlsx(emptyList())

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            assertEquals(0, wb.getSheetAt(0).lastRowNum)
        }
    }

    @Test
    fun buildXlsx_nullFieldsWriteZero() {
        val log = WorkoutLog(sessionId = "s1", exerciseName = "run",
            setNumber = null, reps = null, weight = null, unit = "kg",
            restSeconds = null, timestamp = 1_000_000L)

        val bytes = ExportRepository.buildXlsx(listOf(log))

        XSSFWorkbook(ByteArrayInputStream(bytes)).use { wb ->
            val row = wb.getSheetAt(0).getRow(1)
            assertEquals(0.0, row.getCell(2).numericCellValue, 0.0) // set
            assertEquals(0.0, row.getCell(3).numericCellValue, 0.0) // reps
            assertEquals(0.0, row.getCell(4).numericCellValue, 0.0) // weight
        }
    }
}
