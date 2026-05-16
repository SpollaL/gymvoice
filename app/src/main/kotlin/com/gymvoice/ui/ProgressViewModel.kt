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

enum class ProgressMode { WEIGHT, REPS, VOLUME, E1RM }

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
    val nextSuggestion: String?,
    val isPlateauWarning: Boolean,
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
                    ProgressMode.E1RM ->
                        dayLogs.mapNotNull { log ->
                            val w = log.weight?.takeIf { it > 0f } ?: return@mapNotNull null
                            val r = log.reps?.takeIf { it > 0 } ?: return@mapNotNull null
                            epley(w, r)
                        }.maxOrNull() ?: 0f
                }
            }

        val prValue = pointsPerDate.maxOrNull()
        val prLabel =
            when (mode) {
                ProgressMode.WEIGHT -> prValue?.let { "%.1f kg".format(it) } ?: "-"
                ProgressMode.REPS -> prValue?.toInt()?.let { "$it reps" } ?: "-"
                ProgressMode.VOLUME -> prValue?.toInt()?.let { "$it vol" } ?: "-"
                ProgressMode.E1RM -> prValue?.let { "%.1f e1rm".format(it) } ?: "-"
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
                        ProgressMode.E1RM -> "+%.1f e1rm".format(diff)
                    }
                diff < -0.01f ->
                    when (mode) {
                        ProgressMode.WEIGHT -> "%.1f kg".format(diff)
                        ProgressMode.REPS -> "${diff.toInt()} reps"
                        ProgressMode.VOLUME -> "${diff.toInt()} vol"
                        ProgressMode.E1RM -> "%.1f e1rm".format(diff)
                    }
                else -> "stable"
            }
        val trendUp =
            when {
                diff > 0.01f -> true
                diff < -0.01f -> false
                else -> null
            }

        val isPlateauWarning = computePlateauWarning(byDate)
        val nextSuggestion = computeNextSuggestion(byDate, isPlateauWarning)

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
            nextSuggestion = nextSuggestion,
            isPlateauWarning = isPlateauWarning,
        )
    }

    private fun epley(
        weight: Float,
        reps: Int,
    ): Float = weight * (1f + reps / 30f)

    private fun computePlateauWarning(byDate: List<Map.Entry<java.time.LocalDate, List<WorkoutLog>>>): Boolean {
        if (byDate.size < 3) return false
        val valid =
            byDate
                .takeLast(3)
                .map { (_, dayLogs) ->
                    dayLogs
                        .mapNotNull { log ->
                            val w = log.weight?.takeIf { it > 0f } ?: return@mapNotNull null
                            val r = log.reps?.takeIf { it > 0 } ?: return@mapNotNull null
                            epley(w, r)
                        }.maxOrNull()
                }.filterNotNull()
        return valid.size == 3 &&
            run {
                val maxE1rm = valid.max()
                maxE1rm > 0f && (maxE1rm - valid.min()) / maxE1rm < 0.02f
            }
    }

    private fun computeNextSuggestion(
        byDate: List<Map.Entry<java.time.LocalDate, List<WorkoutLog>>>,
        isPlateauWarning: Boolean,
    ): String? {
        if (byDate.isEmpty()) return null
        val lastDayLogs = byDate.last().value
        return when {
            isPlateauWarning -> "Plateau (3 sessions) — try +2.5kg or vary"
            else -> {
                val weightedSet: (WorkoutLog) -> Triple<Float, Int, Float>? = { log ->
                    val w = log.weight?.takeIf { it > 0f }
                    val r = log.reps?.takeIf { it > 0 }
                    if (w != null && r != null) Triple(w, r, epley(w, r)) else null
                }
                val lastBest = lastDayLogs.mapNotNull(weightedSet).maxByOrNull { it.third }
                val allTimeBest = byDate.flatMap { it.value }.mapNotNull(weightedSet).maxByOrNull { it.third }
                if (lastBest != null) {
                    val baseWeight = maxOf(lastBest.first, allTimeBest?.first ?: 0f)
                    val sets = lastDayLogs.count { kotlin.math.abs((it.weight ?: 0f) - lastBest.first) < 0.01f && (it.reps ?: 0) > 0 }
                    "Next: ${sets}×${lastBest.second} @ %.1fkg".format(baseWeight + 2.5f)
                } else {
                    lastDayLogs
                        .mapNotNull { it.reps?.takeIf { it > 0 } }
                        .maxOrNull()
                        ?.let { "Next: try ${it + 1} reps" }
                }
            }
        }
    }
}
