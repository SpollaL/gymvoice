# Training Log Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Excel (.xlsx) and PDF export of workout logs from the Calendar screen, with a custom date range picker (default: all time), saving to Downloads and triggering the system share sheet.

**Architecture:** `ExportRepository` handles DB query + file generation + Downloads save; `ExportViewModel` (AndroidViewModel) owns UI state; `ExportDialogFragment` shows in Calendar screen via `childFragmentManager`. XLSX uses Apache POI (pure JVM, unit-testable). PDF uses Android's built-in `PdfDocument` (no extra dep).

**Tech Stack:** Apache POI 5.2.5 (`poi-ooxml`), `android.graphics.pdf.PdfDocument`, `MediaStore.Downloads` (API ≥ 29) / `FileProvider` fallback (API 26-28), Kotlin coroutines, Room, ViewBinding.

---

## File Map

| Action | Path |
|--------|------|
| Create | `app/src/main/kotlin/com/gymvoice/data/ExportFormat.kt` |
| Create | `app/src/main/kotlin/com/gymvoice/data/ExportRepository.kt` |
| Create | `app/src/main/kotlin/com/gymvoice/ui/ExportViewModel.kt` |
| Create | `app/src/main/kotlin/com/gymvoice/ui/ExportDialogFragment.kt` |
| Create | `app/src/main/res/layout/dialog_export.xml` |
| Create | `app/src/main/res/drawable/ic_export.xml` |
| Create | `app/src/main/res/xml/file_paths.xml` |
| Create | `app/src/test/kotlin/com/gymvoice/data/ExportRepositoryTest.kt` |
| Create | `app/src/androidTest/kotlin/com/gymvoice/data/WorkoutLogDaoTest.kt` |
| Modify | `gradle/libs.versions.toml` |
| Modify | `app/build.gradle.kts` |
| Modify | `app/src/main/AndroidManifest.xml` |
| Modify | `app/src/main/kotlin/com/gymvoice/data/WorkoutLogDao.kt` |
| Modify | `app/src/main/res/layout/fragment_calendar.xml` |
| Modify | `app/src/main/kotlin/com/gymvoice/ui/CalendarFragment.kt` |

---

## Task 1: Build config — POI + test dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add POI version to libs.versions.toml**

In `gradle/libs.versions.toml`, add to `[versions]`:
```toml
poi = "5.2.5"
```

Add to `[libraries]`:
```toml
poi-ooxml = { group = "org.apache.poi", name = "poi-ooxml", version.ref = "poi" }
```

- [ ] **Step 2: Add POI + test deps to app/build.gradle.kts**

Add inside `dependencies { }` after existing entries:
```kotlin
// Export
implementation(libs.poi.ooxml)

// Tests
testImplementation("junit:junit:4.13.2")
androidTestImplementation("androidx.test.ext:junit:1.1.5")
androidTestImplementation("androidx.room:room-testing:2.7.2")
androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
androidTestImplementation("androidx.test:runner:1.5.2")
```

Add inside `android { }` after `buildFeatures`:
```kotlin
packaging {
    resources {
        excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/INDEX.LIST",
        )
    }
}
```

Add inside `android { defaultConfig { } }`:
```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
```

- [ ] **Step 3: Verify build resolves**

```bash
make build
```

Expected: BUILD SUCCESSFUL (or at least no dependency resolution errors).

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build: add poi-ooxml and test dependencies for export feature"
```

---

## Task 2: FileProvider config

**Files:**
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create file_paths.xml**

Create `app/src/main/res/xml/file_paths.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="downloads" path="." />
    <external-path name="external_downloads" path="Download/" />
</paths>
```

- [ ] **Step 2: Declare FileProvider and storage permission in AndroidManifest.xml**

Replace the entire content of `app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:theme="@style/Theme.GymVoice"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="com.gymvoice.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

- [ ] **Step 3: Build to verify manifest is valid**

```bash
make build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/xml/file_paths.xml app/src/main/AndroidManifest.xml
git commit -m "feat: add FileProvider config for export file sharing"
```

---

## Task 3: ExportFormat enum

**Files:**
- Create: `app/src/main/kotlin/com/gymvoice/data/ExportFormat.kt`

- [ ] **Step 1: Create ExportFormat.kt**

```kotlin
package com.gymvoice.data

enum class ExportFormat { XLSX, PDF }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/gymvoice/data/ExportFormat.kt
git commit -m "feat: add ExportFormat enum"
```

---

## Task 4: DAO — add getLogsInRange

**Files:**
- Modify: `app/src/main/kotlin/com/gymvoice/data/WorkoutLogDao.kt`
- Create: `app/src/androidTest/kotlin/com/gymvoice/data/WorkoutLogDaoTest.kt`

- [ ] **Step 1: Write the failing instrumented test**

Create `app/src/androidTest/kotlin/com/gymvoice/data/WorkoutLogDaoTest.kt`:
```kotlin
package com.gymvoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutLogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutLogDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = db.workoutLogDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun getLogsInRange_returnsOnlyLogsWithinRange() = runTest {
        dao.insert(WorkoutLog(sessionId = "s1", exerciseName = "bench", setNumber = 1,
            reps = 10, weight = 80f, unit = "kg", timestamp = 1000L))
        dao.insert(WorkoutLog(sessionId = "s2", exerciseName = "squat", setNumber = 1,
            reps = 5, weight = 100f, unit = "kg", timestamp = 5000L))
        dao.insert(WorkoutLog(sessionId = "s3", exerciseName = "row", setNumber = 1,
            reps = 8, weight = 60f, unit = "kg", timestamp = 9000L))

        val result = dao.getLogsInRange(start = 1000L, end = 5000L)

        assertEquals(2, result.size)
        assertEquals("bench", result[0].exerciseName)
        assertEquals("squat", result[1].exerciseName)
    }

    @Test
    fun getLogsInRange_allTime_returnsAll() = runTest {
        dao.insert(WorkoutLog(sessionId = "s1", exerciseName = "bench", setNumber = 1,
            reps = 10, weight = 80f, unit = "kg", timestamp = 1000L))
        dao.insert(WorkoutLog(sessionId = "s2", exerciseName = "squat", setNumber = 1,
            reps = 5, weight = 100f, unit = "kg", timestamp = 5000L))

        val result = dao.getLogsInRange(start = 0L, end = Long.MAX_VALUE)

        assertEquals(2, result.size)
    }

    @Test
    fun getLogsInRange_emptyRange_returnsEmpty() = runTest {
        dao.insert(WorkoutLog(sessionId = "s1", exerciseName = "bench", setNumber = 1,
            reps = 10, weight = 80f, unit = "kg", timestamp = 1000L))

        val result = dao.getLogsInRange(start = 2000L, end = 3000L)

        assertEquals(0, result.size)
    }
}
```

- [ ] **Step 2: Add getLogsInRange to WorkoutLogDao.kt**

In `app/src/main/kotlin/com/gymvoice/data/WorkoutLogDao.kt`, add after `getLatestTimestamp()`:
```kotlin
@Query("SELECT * FROM logs WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
suspend fun getLogsInRange(start: Long, end: Long): List<WorkoutLog>
```

- [ ] **Step 3: Run the instrumented tests**

```bash
make build
# Connect device or start emulator, then:
./gradlew connectedAndroidTest --tests "com.gymvoice.data.WorkoutLogDaoTest"
```

Expected: 3 tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/gymvoice/data/WorkoutLogDao.kt \
        app/src/androidTest/kotlin/com/gymvoice/data/WorkoutLogDaoTest.kt
git commit -m "feat: add getLogsInRange DAO query with instrumented tests"
```

---

## Task 5: ExportRepository — XLSX generation + unit test

**Files:**
- Create: `app/src/main/kotlin/com/gymvoice/data/ExportRepository.kt`
- Create: `app/src/test/kotlin/com/gymvoice/data/ExportRepositoryTest.kt`

- [ ] **Step 1: Write the failing unit test**

Create `app/src/test/kotlin/com/gymvoice/data/ExportRepositoryTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test --tests "com.gymvoice.data.ExportRepositoryTest"
```

Expected: FAIL — `ExportRepository` does not exist yet.

- [ ] **Step 3: Create ExportRepository.kt with buildXlsx**

Create `app/src/main/kotlin/com/gymvoice/data/ExportRepository.kt`:
```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
./gradlew test --tests "com.gymvoice.data.ExportRepositoryTest"
```

Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/gymvoice/data/ExportRepository.kt \
        app/src/test/kotlin/com/gymvoice/data/ExportRepositoryTest.kt
git commit -m "feat: add ExportRepository with XLSX generation and unit tests"
```

---

## Task 6: ExportRepository — PDF generation

**Files:**
- Modify: `app/src/main/kotlin/com/gymvoice/data/ExportRepository.kt`

- [ ] **Step 1: Replace the buildPdf stub with full implementation**

Replace the `buildPdf` function inside the `companion object` in `ExportRepository.kt`:
```kotlin
fun buildPdf(logs: List<WorkoutLog>, fromMs: Long, toMs: Long): ByteArray {
    val document = android.graphics.pdf.PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 40f
    val lineHeight = 16f
    val bottomLimit = pageHeight - 40f

    val titlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK; textSize = 14f
        isFakeBoldText = true; isAntiAlias = true
    }
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK; textSize = 12f
        isFakeBoldText = true; isAntiAlias = true
    }
    val bodyPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK; textSize = 11f; isAntiAlias = true
    }
    val columnPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY; textSize = 10f; isAntiAlias = true
    }
    val footerPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.DKGRAY; textSize = 9f; isAntiAlias = true
    }

    var pageNum = 1
    var page = document.startPage(
        android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
    )
    var canvas = page.canvas
    var y = margin

    fun finishPage() {
        canvas.drawText(
            "$pageNum",
            (pageWidth / 2).toFloat(),
            bottomLimit + 20f,
            footerPaint,
        )
        document.finishPage(page)
    }

    fun newPage() {
        finishPage()
        pageNum++
        page = document.startPage(
            android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
        )
        canvas = page.canvas
        y = margin
    }

    fun writeLine(text: String, x: Float, paint: android.graphics.Paint) {
        if (y > bottomLimit) newPage()
        canvas.drawText(text, x, y, paint)
        y += lineHeight
    }

    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val title = if (fromMs == 0L && toMs == Long.MAX_VALUE) {
        "GymVoice Training Log — All Time"
    } else {
        "GymVoice Training Log — ${fmt.format(Date(fromMs))} to ${fmt.format(Date(toMs))}"
    }

    writeLine(title, margin, titlePaint)
    y += lineHeight / 2

    for ((date, byExercise) in groupByDateAndExercise(logs)) {
        y += lineHeight / 2
        writeLine(date, margin, labelPaint)
        for ((exercise, sets) in byExercise) {
            writeLine("  $exercise", margin + 8f, bodyPaint)
            writeLine("  Set  Reps  Weight  Unit  Rest(s)", margin + 16f, columnPaint)
            for (log in sets) {
                writeLine(
                    "  ${log.setNumber ?: "—"}     ${log.reps ?: "—"}     " +
                        "${log.weight ?: "—"}  ${log.unit}     ${log.restSeconds ?: "—"}",
                    margin + 16f,
                    bodyPaint,
                )
            }
            y += 4f
        }
        y += 8f
    }

    finishPage()
    val out = ByteArrayOutputStream()
    document.writeTo(out)
    document.close()
    return out.toByteArray()
}
```

- [ ] **Step 2: Build to verify no compile errors**

```bash
make build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/gymvoice/data/ExportRepository.kt
git commit -m "feat: add PDF generation to ExportRepository"
```

---

## Task 7: ExportViewModel

**Files:**
- Create: `app/src/main/kotlin/com/gymvoice/ui/ExportViewModel.kt`

- [ ] **Step 1: Create ExportViewModel.kt**

```kotlin
package com.gymvoice.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.ExportFormat
import com.gymvoice.data.ExportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExportViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).workoutLogDao()
    private val repository = ExportRepository(dao, app)

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Success(val uri: Uri) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    var fromMs: Long = 0L
    var toMs: Long = Long.MAX_VALUE
    var format: ExportFormat = ExportFormat.XLSX

    fun export() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            _uiState.value = when (val result = repository.export(format, fromMs, toMs)) {
                is ExportRepository.ExportResult.Success -> UiState.Success(result.uri)
                is ExportRepository.ExportResult.Failure -> UiState.Error(result.message)
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
make build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/gymvoice/ui/ExportViewModel.kt
git commit -m "feat: add ExportViewModel with UiState flow"
```

---

## Task 8: Export dialog layout + export icon drawable

**Files:**
- Create: `app/src/main/res/layout/dialog_export.xml`
- Create: `app/src/main/res/drawable/ic_export.xml`

- [ ] **Step 1: Create dialog_export.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="8dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="From"
            android:textColor="@color/subtext0"
            android:textSize="12sp"
            android:fontFamily="monospace" />

        <TextView
            android:id="@+id/tvFromDate"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="All time"
            android:textColor="@color/mauve"
            android:textSize="12sp"
            android:fontFamily="monospace"
            android:padding="4dp"
            android:background="?attr/selectableItemBackground" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:layout_marginBottom="16dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="To"
            android:textColor="@color/subtext0"
            android:textSize="12sp"
            android:fontFamily="monospace" />

        <TextView
            android:id="@+id/tvToDate"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="@color/mauve"
            android:textSize="12sp"
            android:fontFamily="monospace"
            android:padding="4dp"
            android:background="?attr/selectableItemBackground" />
    </LinearLayout>

    <com.google.android.material.chip.ChipGroup
        android:id="@+id/chipGroupFormat"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        app:singleSelection="true"
        app:selectionRequired="true">

        <com.google.android.material.chip.Chip
            android:id="@+id/chipExcel"
            style="@style/Widget.MaterialComponents.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:checked="true"
            android:text="Excel"
            android:fontFamily="monospace"
            android:textSize="12sp" />

        <com.google.android.material.chip.Chip
            android:id="@+id/chipPdf"
            style="@style/Widget.MaterialComponents.Chip.Filter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="PDF"
            android:fontFamily="monospace"
            android:textSize="12sp" />

    </com.google.android.material.chip.ChipGroup>

    <TextView
        android:id="@+id/tvEmptyHint"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="No logs in selected range"
        android:textColor="@color/overlay0"
        android:textSize="11sp"
        android:fontFamily="monospace"
        android:visibility="gone"
        android:layout_marginTop="8dp" />

</LinearLayout>
```

- [ ] **Step 2: Create ic_export.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M12,3v12M8,11l4,4 4,-4M5,20h14"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

- [ ] **Step 3: Build to verify**

```bash
make build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/dialog_export.xml \
        app/src/main/res/drawable/ic_export.xml
git commit -m "feat: add export dialog layout and export icon drawable"
```

---

## Task 9: ExportDialogFragment

**Files:**
- Create: `app/src/main/kotlin/com/gymvoice/ui/ExportDialogFragment.kt`

- [ ] **Step 1: Create ExportDialogFragment.kt**

```kotlin
package com.gymvoice.ui

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gymvoice.data.ExportFormat
import com.gymvoice.databinding.DialogExportBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExportDialogFragment : DialogFragment() {
    private lateinit var binding: DialogExportBinding
    private val viewModel: ExportViewModel by viewModels()

    private var fromMs = 0L
    private var toMs = Long.MAX_VALUE
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogExportBinding.inflate(layoutInflater)

        binding.tvFromDate.text = getString(com.gymvoice.R.string.export_all_time)
        binding.tvToDate.text = dateFmt.format(Date(System.currentTimeMillis()))

        binding.tvFromDate.setOnClickListener { pickFromDate() }
        binding.tvToDate.setOnClickListener { pickToDate() }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(com.gymvoice.R.string.export_title)
            .setView(binding.root)
            .setPositiveButton(com.gymvoice.R.string.export_button, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val exportBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            exportBtn.setOnClickListener { triggerExport() }
            observeState(dialog, exportBtn)
        }

        return dialog
    }

    private fun observeState(dialog: AlertDialog, exportBtn: Button) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ExportViewModel.UiState.Loading -> {
                            exportBtn.isEnabled = false
                            exportBtn.text = getString(com.gymvoice.R.string.export_exporting)
                        }
                        is ExportViewModel.UiState.Success -> {
                            Toast.makeText(
                                requireContext(),
                                com.gymvoice.R.string.export_saved,
                                Toast.LENGTH_SHORT,
                            ).show()
                            shareFile(state.uri)
                            dialog.dismiss()        // stop lifecycle → stops collection before resetState emits
                            viewModel.resetState()
                        }
                        is ExportViewModel.UiState.Error -> {
                            exportBtn.isEnabled = true
                            exportBtn.text = getString(com.gymvoice.R.string.export_button)
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetState()
                        }
                        ExportViewModel.UiState.Idle -> {
                            exportBtn.isEnabled = true
                            exportBtn.text = getString(com.gymvoice.R.string.export_button)
                        }
                    }
                }
            }
        }
    }

    private fun triggerExport() {
        viewModel.fromMs = fromMs
        viewModel.toMs = toMs
        viewModel.format = if (binding.chipExcel.isChecked) ExportFormat.XLSX else ExportFormat.PDF
        viewModel.export()
    }

    private fun pickFromDate() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (fromMs == 0L) System.currentTimeMillis() else fromMs
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                fromMs = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                binding.tvFromDate.text = dateFmt.format(Date(fromMs))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun pickToDate() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (toMs == Long.MAX_VALUE) System.currentTimeMillis() else toMs
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                toMs = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                binding.tvToDate.text = dateFmt.format(Date(toMs))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun shareFile(uri: Uri) {
        val mimeType = if (binding.chipExcel.isChecked) {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        } else {
            "application/pdf"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(com.gymvoice.R.string.export_share_title)))
    }
}
```

- [ ] **Step 2: Add string resources**

In `app/src/main/res/values/strings.xml`, add inside `<resources>`:
```xml
<string name="export_title">Export Training Log</string>
<string name="export_button">Export</string>
<string name="export_exporting">Exporting…</string>
<string name="export_all_time">All time</string>
<string name="export_saved">Saved to Downloads</string>
<string name="export_share_title">Share training log</string>
```

- [ ] **Step 3: Build to verify**

```bash
make build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/gymvoice/ui/ExportDialogFragment.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat: add ExportDialogFragment with date pickers and format selection"
```

---

## Task 10: Wire CalendarFragment

**Files:**
- Modify: `app/src/main/res/layout/fragment_calendar.xml`
- Modify: `app/src/main/kotlin/com/gymvoice/ui/CalendarFragment.kt`

- [ ] **Step 1: Add export button to fragment_calendar.xml**

In `fragment_calendar.xml`, add an `ImageButton` for export in the top header `LinearLayout`, right after `btnNextMonth`:
```xml
<ImageButton
    android:id="@+id/btnExport"
    android:layout_width="32dp"
    android:layout_height="32dp"
    android:src="@drawable/ic_export"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="@null"
    android:tint="@color/subtext0"
    android:layout_marginStart="4dp" />
```

The header `LinearLayout` after this change:
```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingHorizontal="12dp"
    android:paddingTop="20dp"
    android:paddingBottom="8dp"
    android:gravity="center_vertical">

    <ImageButton
        android:id="@+id/btnPrevMonth"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:src="@drawable/ic_chevron_left"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@null"
        android:tint="@color/subtext0" />

    <TextView
        android:id="@+id/tvMonthYear"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center"
        android:textSize="14sp"
        android:textColor="@color/text"
        android:fontFamily="monospace"
        android:letterSpacing="0.1" />

    <ImageButton
        android:id="@+id/btnNextMonth"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:src="@drawable/ic_chevron_right"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@null"
        android:tint="@color/subtext0" />

    <ImageButton
        android:id="@+id/btnExport"
        android:layout_width="32dp"
        android:layout_height="32dp"
        android:src="@drawable/ic_export"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:contentDescription="@null"
        android:tint="@color/subtext0"
        android:layout_marginStart="4dp" />

</LinearLayout>
```

- [ ] **Step 2: Wire export button in CalendarFragment.kt**

In `CalendarFragment.kt`, add the following line inside `setupCalendar()`, after the `binding.btnAddLog.setOnClickListener` line:
```kotlin
binding.btnExport.setOnClickListener {
    ExportDialogFragment().show(childFragmentManager, "export")
}
```

- [ ] **Step 3: Build**

```bash
make build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/fragment_calendar.xml \
        app/src/main/kotlin/com/gymvoice/ui/CalendarFragment.kt
git commit -m "feat: wire export button in CalendarFragment to ExportDialogFragment"
```

---

## Task 11: Pre-commit quality check + final commit

**Files:** (all modified)

- [ ] **Step 1: Run full pre-commit suite**

```bash
make pre-commit
```

Expected: format + lint + build all pass. If ktlint auto-fixes any files, the output will show which files changed — that's expected, just continue.

- [ ] **Step 2: Fix any detekt violations**

If `make pre-commit` reports detekt errors, address each one. Common issues:
- Long functions: extract private helpers
- Magic numbers: extract as `private const val`
- Unused imports: remove them

Re-run `make pre-commit` after each fix until clean.

- [ ] **Step 3: Stage and commit any auto-fixed files**

```bash
git add -p   # review all changes
git commit -m "style: apply ktlint formatting for export feature"
```

- [ ] **Step 4: Manual smoke test on device**

1. `make flash` (build + install on connected device/emulator)
2. Open app → Calendar tab
3. Tap the export icon (download arrow) in top-right of calendar header
4. Verify dialog opens with "From: All time", today's date in "To", Excel chip selected
5. Tap "From" → date picker opens → pick a date → verify date updates in dialog
6. Tap "Export" → toast "Saved to Downloads" appears → share sheet opens
7. Dismiss share sheet → dialog closes
8. Switch to PDF chip → tap Export → verify PDF share sheet opens
9. Open device Files app → Downloads folder → verify both files exist

- [ ] **Step 5: Final commit**

```bash
git add .
git commit -m "feat: training log export (Excel + PDF) from Calendar screen"
```
