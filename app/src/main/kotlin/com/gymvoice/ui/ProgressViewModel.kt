package com.gymvoice.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.Exercise
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

enum class ProgressMode { WEIGHT, REPS, VOLUME, E1RM }

enum class VolumeStatus { BELOW_MEV, IN_MAV, ABOVE_MRV }

data class MuscleVolumeEntry(
    val muscle: String,
    val sets: Int,
    val status: VolumeStatus,
)

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
    private val exerciseDao = AppDatabase.getInstance(app).exerciseDao()

    val exercises: StateFlow<List<String>> =
        dao.getDistinctExercises()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weeklyVolume: StateFlow<List<MuscleVolumeEntry>> =
        combine(
            dao.getLogsAfter(mondayStartMillis()),
            exerciseDao.getAll(),
        ) { logs, exercises -> computeWeeklyVolume(logs, exercises) }
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

    private fun mondayStartMillis(): Long {
        val zone = ZoneId.systemDefault()
        val monday = java.time.LocalDate.now(zone).with(DayOfWeek.MONDAY)
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun computeWeeklyVolume(
        logs: List<WorkoutLog>,
        exercises: List<Exercise>,
    ): List<MuscleVolumeEntry> {
        val muscleMap = exercises.associate { it.name.lowercase() to it.muscleGroup }
        val setsByMuscle = mutableMapOf<String, Int>()
        logs.filter { (it.reps ?: 0) > 0 }
            .groupBy { "${it.sessionId}|${it.exerciseName}" }
            .forEach { (_, group) ->
                val maxW = group.mapNotNull { it.weight }.maxOrNull() ?: 0f
                val workingSets =
                    if (maxW > 0f) {
                        group.filter { (it.weight ?: 0f) >= maxW * WARMUP_THRESHOLD }
                            .sumOf { it.setNumber ?: 1 }
                    } else {
                        group.sumOf { it.setNumber ?: 1 }
                    }
                val muscle = muscleMap[group.first().exerciseName.lowercase()] ?: return@forEach
                setsByMuscle[muscle] = (setsByMuscle[muscle] ?: 0) + workingSets
            }
        return MUSCLE_ORDER.map { muscle ->
            val sets = setsByMuscle[muscle] ?: 0
            MuscleVolumeEntry(
                muscle,
                sets,
                when {
                    sets < MEV_SETS -> VolumeStatus.BELOW_MEV
                    sets <= MRV_SETS -> VolumeStatus.IN_MAV
                    else -> VolumeStatus.ABOVE_MRV
                },
            )
        }
    }

    private fun computeNextSuggestion(
        byDate: List<Map.Entry<java.time.LocalDate, List<WorkoutLog>>>,
        isPlateauWarning: Boolean,
    ): String? {
        if (byDate.isEmpty()) return null
        val allLogs = byDate.flatMap { it.value }
        val latestTs = allLogs.maxOf { it.timestamp }
        val lastSessionLogs = allLogs.filter { latestTs - it.timestamp < SESSION_WINDOW_MS }
        return when {
            isPlateauWarning -> "Plateau (3 sessions) — deload or vary stimulus"
            else -> doubleProgressionSuggestion(lastSessionLogs, byDate)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun doubleProgressionSuggestion(
        lastDayLogs: List<WorkoutLog>,
        byDate: List<Map.Entry<java.time.LocalDate, List<WorkoutLog>>>,
    ): String? {
        val workingWeight =
            lastDayLogs
                .filter { (it.reps ?: 0) > 0 && (it.weight ?: 0f) > 0f }
                .maxOfOrNull { it.weight!! }
        if (workingWeight == null) return bodyweightSuggestion(lastDayLogs)
        val workingSets =
            lastDayLogs
                .filter { kotlin.math.abs((it.weight ?: 0f) - workingWeight) < 0.01f && (it.reps ?: 0) > 0 }
                .sumOf { it.setNumber ?: 1 }
        val lastBestReps =
            lastDayLogs
                .filter { kotlin.math.abs((it.weight ?: 0f) - workingWeight) < 0.01f }
                .mapNotNull { it.reps?.takeIf { it > 0 } }
                .maxOrNull()
        val allTimeBestReps =
            byDate.flatMap { it.value }
                .filter { kotlin.math.abs((it.weight ?: 0f) - workingWeight) < 0.01f }
                .mapNotNull { it.reps?.takeIf { it > 0 } }
                .maxOrNull() ?: (lastBestReps ?: 0)
        return lastBestReps?.let { reps ->
            if (allTimeBestReps >= HYPERTROPHY_CEILING) {
                "Next: $workingSets×${HYPERTROPHY_FLOOR} @ %.1fkg".format(workingWeight + 2.5f)
            } else {
                "Next: $workingSets×${reps + 1} @ %.1fkg".format(workingWeight)
            }
        }
    }

    private fun bodyweightSuggestion(lastDayLogs: List<WorkoutLog>): String? {
        val maxReps = lastDayLogs.mapNotNull { it.reps?.takeIf { it > 0 } }.maxOrNull()
        return maxReps?.let {
            if (it < HYPERTROPHY_CEILING) "Next: try ${it + 1} reps" else "Next: add resistance"
        }
    }

    companion object {
        private const val MEV_SETS = 10
        private const val MRV_SETS = 20
        private const val WARMUP_THRESHOLD = 0.8f
        private const val HYPERTROPHY_CEILING = 12
        private const val HYPERTROPHY_FLOOR = 8
        private const val SESSION_WINDOW_MS = 6 * 60 * 60 * 1000L
        private val MUSCLE_ORDER = listOf("Arms", "Back", "Chest", "Core", "Legs", "Shoulders")
    }
}
