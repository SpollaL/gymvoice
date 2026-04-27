package com.gymvoice.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.WorkoutLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId

data class ProgressData(
    val logs: List<WorkoutLog>,
    val sparklinePoints: List<Float>,
    val prLabel: String,
    val prValue: Float?,
    val totalSessions: Int,
    val trendLabel: String,
    val trendUp: Boolean?,
    val hasWeight: Boolean,
)

class ProgressViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).workoutLogDao()

    val exercises: StateFlow<List<String>> =
        dao.getDistinctExercises()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedExercise = MutableStateFlow<String?>(null)
    val selectedExercise: StateFlow<String?> = _selectedExercise

    @OptIn(ExperimentalCoroutinesApi::class)
    val progressData: Flow<ProgressData?> =
        _selectedExercise
            .flatMapLatest { name -> if (name == null) flowOf(emptyList()) else dao.getLogsForExercise(name) }
            .map { logs -> if (logs.isEmpty()) null else computeProgressData(logs) }

    fun selectExercise(name: String) {
        _selectedExercise.value = name
    }

    private fun computeProgressData(logs: List<WorkoutLog>): ProgressData {
        val zone = ZoneId.systemDefault()
        val hasWeight = logs.any { (it.weight ?: 0f) > 0f }

        val byDate =
            logs
                .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
                .entries
                .sortedBy { it.key }

        val pointsPerDate =
            byDate.map { (_, dayLogs) ->
                if (hasWeight) {
                    dayLogs.mapNotNull { it.weight }.maxOrNull() ?: 0f
                } else {
                    dayLogs.mapNotNull { it.reps?.toFloat() }.maxOrNull() ?: 0f
                }
            }

        val prValue = pointsPerDate.maxOrNull()
        val prLabel =
            if (hasWeight) {
                prValue?.let { "%.1f kg".format(it) } ?: "-"
            } else {
                prValue?.toInt()?.let { "$it reps" } ?: "-"
            }

        val firstVal = pointsPerDate.firstOrNull() ?: 0f
        val lastVal = pointsPerDate.lastOrNull() ?: 0f
        val diff = lastVal - firstVal

        val trendLabel =
            when {
                diff > 0.01f -> if (hasWeight) "+%.1f kg".format(diff) else "+${diff.toInt()} reps"
                diff < -0.01f -> if (hasWeight) "%.1f kg".format(diff) else "${diff.toInt()} reps"
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
        )
    }
}
