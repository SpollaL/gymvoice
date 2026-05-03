# Training Log Export — Design Spec
**Date:** 2026-05-03  
**Feature:** Download training log as Excel (.xlsx) or PDF

---

## 1. Overview

Add export capability to the Calendar screen. User picks a date range (default: all time), selects Excel or PDF, and receives the file saved to Downloads plus an Android share sheet.

---

## 2. Scope

- Entry point: `CalendarFragment` toolbar icon
- Date range: custom from/to, defaulting to epoch → now (all-time)
- Formats: XLSX (Apache POI) and PDF (Android `PdfDocument`)
- Delivery: save to device Downloads folder + system share sheet

---

## 3. Architecture

### New Files

| File | Responsibility |
|------|---------------|
| `data/ExportRepository.kt` | Query DB by range, generate XLSX/PDF bytes, save to Downloads, return `Uri` |
| `ui/ExportViewModel.kt` | Date range state, format selection, `UiState`, calls repository |
| `ui/ExportDialogFragment.kt` | Modal dialog: date pickers, format toggle, export button |
| `res/layout/dialog_export.xml` | Layout for export dialog |
| `res/xml/file_paths.xml` | FileProvider path configuration |

### Modified Files

| File | Change |
|------|--------|
| `data/WorkoutLogDao.kt` | Add `suspend fun getLogsInRange(start: Long, end: Long): List<WorkoutLog>` |
| `ui/CalendarFragment.kt` | Add toolbar export icon, open `ExportDialogFragment` |
| `app/build.gradle.kts` | Add `poi-ooxml` dependency, configure FileProvider |
| `AndroidManifest.xml` | Declare FileProvider; add `WRITE_EXTERNAL_STORAGE` for API < 29 |

---

## 4. Data Layer

### New DAO Query
```kotlin
@Query("SELECT * FROM logs WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
suspend fun getLogsInRange(start: Long, end: Long): List<WorkoutLog>
```

All-time default: `start = 0L`, `end = Long.MAX_VALUE`.

### ExportRepository API
```kotlin
sealed class ExportResult {
    data class Success(val uri: Uri) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

suspend fun exportXlsx(logs: List<WorkoutLog>, fromMs: Long, toMs: Long): ExportResult
suspend fun exportPdf(logs: List<WorkoutLog>, fromMs: Long, toMs: Long): ExportResult
```

---

## 5. File Layout

### Excel (.xlsx)
- Single sheet: "Training Log"
- Bold header row: `Date | Exercise | Set | Reps | Weight | Unit | Rest (s)`
- One row per `WorkoutLog`, sorted by timestamp ASC
- Auto-sized columns

### PDF
- Title: "GymVoice Training Log — {from} to {to}" (or "All Time")
- Grouped by date, then by exercise name
- Per exercise: table with `Set | Reps | Weight | Unit | Rest (s)` columns
- Page numbers in footer

### File Naming
- Range: `gymvoice_log_2026-01-01_to_2026-05-03.xlsx`
- All-time: `gymvoice_log_all_time.xlsx` / `.pdf`

---

## 6. Storage Strategy

| API Level | Mechanism | Permission Required |
|-----------|-----------|-------------------|
| ≥ 29 | `MediaStore.Downloads` | None |
| < 29 | `Environment.DIRECTORY_DOWNLOADS` | `WRITE_EXTERNAL_STORAGE` |

Share: `FileProvider` (authority: `com.gymvoice.fileprovider`) URI → `Intent.ACTION_SEND` → system chooser.

---

## 7. UI Flow

```
CalendarFragment toolbar
  └─ export icon tap
       └─ ExportDialogFragment opens
            ├─ From date picker (default: all time start)
            ├─ To date picker   (default: today)
            ├─ Format chips: [Excel] [PDF]  (Excel selected by default)
            ├─ Empty-range guard: disable Export if 0 logs in range
            └─ Export tap
                 ├─ spinner, button disabled
                 ├─ success → toast "Saved to Downloads" + share sheet
                 └─ error   → toast message, dialog stays open
```

### ExportViewModel UiState
```kotlin
sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Success(val uri: Uri) : UiState()
    data class Error(val message: String) : UiState()
}
```

---

## 8. Dependencies

```kotlin
// app/build.gradle.kts
implementation("org.apache.poi:poi-ooxml:5.2.5")
```

Android `PdfDocument` is part of the platform SDK — no extra dependency.

---

## 9. Out of Scope

- Per-exercise filtering (export all exercises only)
- Custom column selection
- Cloud sync / email send (handled by share sheet)
- Progress charts in PDF
