package com.gymvoice.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.WorkoutLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class ProgressMode { WEIGHT, REPS, VOLUME }

data class ProgressData(
    val logs: List<WorkoutLog>,
    val sparklinePoints: List<Float>,
    val prLabel: String,
    val prValue: Float?,
    val totalSessions: Int,
    val trendLabel: String,
    val trendUp: Boolean?,
    val hasWeight: Boolean,
    val mode: ProgressMode,
)

class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).workoutLogDao()

    val exercises: StateFlow<List<String>> =
        dao.getDistinctExercises()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedExercise = MutableStateFlow<String?>(null)
    val selectedExercise: StateFlow<String?> = _selectedExercise

    private val _mode = MutableStateFlow(ProgressMode.WEIGHT)
    val mode: StateFlow<ProgressMode> = _mode

    @OptIn(ExperimentalCoroutinesApi::class)
    val progressData: Flow<ProgressData?> =
        _selectedExercise
            .flatMapLatest { name -> if (name == null) flowOf(emptyList()) else dao.getLogsForExercise(name) }
            .combine(_mode) { logs, mode -> logs to mode }
            .map { (logs, mode) -> if (logs.isEmpty()) null else computeProgressData(logs, mode) }

    private val _cloneEvent = MutableSharedFlow<WorkoutLog>(extraBufferCapacity = 1)
    val cloneEvent: SharedFlow<WorkoutLog> = _cloneEvent.asSharedFlow()

    fun cloneLog(log: WorkoutLog) =
        viewModelScope.launch {
            val newId =
                dao.insert(
                    log.copy(id = 0, sessionId = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis()),
                )
            _cloneEvent.emit(log.copy(id = newId))
        }

    fun deleteLog(log: WorkoutLog) = viewModelScope.launch { dao.delete(log) }

    fun selectExercise(name: String) {
        _selectedExercise.value = name
    }

    fun setMode(mode: ProgressMode) {
        _mode.value = mode
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun computeProgressData(
        logs: List<WorkoutLog>,
        mode: ProgressMode,
    ): ProgressData {
        val zone = ZoneId.systemDefault()
        val hasWeight = logs.any { (it.weight ?: 0f) > 0f }

        val byDate =
            logs
                .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
                .entries
                .sortedBy { it.key }

        val pointsPerDate =
            byDate.map { (_, dayLogs) ->
                when (mode) {
                    ProgressMode.WEIGHT -> dayLogs.mapNotNull { it.weight }.maxOrNull() ?: 0f
                    ProgressMode.REPS -> dayLogs.mapNotNull { it.reps?.toFloat() }.maxOrNull() ?: 0f
                    ProgressMode.VOLUME ->
                        dayLogs.sumOf { ((it.weight ?: 0f) * (it.reps ?: 0)).toDouble() }.toFloat()
                }
            }

        val prValue = pointsPerDate.maxOrNull()
        val prLabel =
            when (mode) {
                ProgressMode.WEIGHT -> prValue?.let { "%.1f kg".format(it) } ?: "-"
                ProgressMode.REPS -> prValue?.toInt()?.let { "$it reps" } ?: "-"
                ProgressMode.VOLUME -> prValue?.toInt()?.let { "$it vol" } ?: "-"
            }

        val firstVal = pointsPerDate.firstOrNull() ?: 0f
        val lastVal = pointsPerDate.lastOrNull() ?: 0f
        val diff = lastVal - firstVal

        val trendLabel =
            when {
                diff > 0.01f ->
                    when (mode) {
                        ProgressMode.WEIGHT -> "+%.1f kg".format(diff)
                        ProgressMode.REPS -> "+${diff.toInt()} reps"
                        ProgressMode.VOLUME -> "+${diff.toInt()} vol"
                    }
                diff < -0.01f ->
                    when (mode) {
                        ProgressMode.WEIGHT -> "%.1f kg".format(diff)
                        ProgressMode.REPS -> "${diff.toInt()} reps"
                        ProgressMode.VOLUME -> "${diff.toInt()} vol"
                    }
                else -> "stable"
            }
        val trendUp =
            when {
                diff > 0.01f -> true
                diff < -0.01f -> false
                else -> null
            }

        return ProgressData(
            logs = logs.sortedByDescending { it.timestamp },
            sparklinePoints = pointsPerDate,
            prLabel = prLabel,
            prValue = prValue,
            totalSessions = byDate.size,
            trendLabel = trendLabel,
            trendUp = trendUp,
            hasWeight = hasWeight,
            mode = mode,
        )
    }
}
