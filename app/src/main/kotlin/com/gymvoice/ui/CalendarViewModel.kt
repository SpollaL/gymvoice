package com.gymvoice.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.UserCorrection
import com.gymvoice.data.WorkoutLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).workoutLogDao()
    private val correctionDao = AppDatabase.getInstance(app).userCorrectionDao()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _activeDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val activeDates: StateFlow<Set<LocalDate>> = _activeDates

    @OptIn(ExperimentalCoroutinesApi::class)
    val logsForDate: Flow<List<WorkoutLog>> =
        _selectedDate.flatMapLatest { date ->
            val zone = ZoneId.systemDefault()
            val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            dao.getLogsForDate(start, end)
        }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun loadMonth(yearMonth: YearMonth) {
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val start = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            _activeDates.value =
                dao.getTimestampsInRange(start, end)
                    .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                    .toSet()
        }
    }

    fun saveCorrection(
        original: String,
        corrected: String,
    ) = viewModelScope.launch {
        if (original.isBlank() || corrected.isBlank() || original == corrected) return@launch
        correctionDao.upsert(
            UserCorrection(
                sttFragment = original.lowercase().trim(),
                correctedExercise = corrected.lowercase().trim(),
            ),
        )
    }

    fun updateLog(log: WorkoutLog) = viewModelScope.launch { dao.update(log) }

    fun deleteLog(log: WorkoutLog) = viewModelScope.launch { dao.delete(log) }
}
