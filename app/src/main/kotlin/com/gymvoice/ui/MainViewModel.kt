package com.gymvoice.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.audio.SpeechRecognizerSTT
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.Exercise
import com.gymvoice.data.UserCorrection
import com.gymvoice.data.WorkoutLog
import com.gymvoice.ml.ExerciseMatch
import com.gymvoice.ml.ExerciseMatcher
import com.gymvoice.ml.GemmaInference
import com.gymvoice.ml.ParsedWorkout
import com.gymvoice.ml.WorkoutParser
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

private const val MAX_INFERRED_REST = 1800

@Suppress("TooManyFunctions")
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).workoutLogDao()
    private val correctionDao = AppDatabase.getInstance(app).userCorrectionDao()
    private val exerciseDao = AppDatabase.getInstance(app).exerciseDao()
    private val stt = SpeechRecognizerSTT(app)
    private val gemma = GemmaInference()

    private var userCorrections = mapOf<String, String>()
    private val extraExercises = mutableListOf<Exercise>()
    private val allExercisesDeferred: Deferred<List<Exercise>> =
        viewModelScope.async {
            exerciseDao.getAllList()
        }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _lastLogged = MutableSharedFlow<WorkoutLog>(extraBufferCapacity = 1)
    val lastLogged: SharedFlow<WorkoutLog> = _lastLogged.asSharedFlow()

    private val _cloneEvent = MutableSharedFlow<WorkoutLog>(extraBufferCapacity = 1)
    val cloneEvent: SharedFlow<WorkoutLog> = _cloneEvent.asSharedFlow()

    private val _pendingLog = MutableStateFlow<PendingLog?>(null)
    val pendingLog: StateFlow<PendingLog?> = _pendingLog.asStateFlow()

    private val _pendingConfirmation = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pendingConfirmation: SharedFlow<Unit> = _pendingConfirmation.asSharedFlow()

    private val _standardizeResult = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val standardizeResult: SharedFlow<String> = _standardizeResult.asSharedFlow()

    val todayLogs = dao.getTodayLogs(startOfToday())

    init {
        viewModelScope.launch {
            userCorrections = correctionDao.getAll().associate { it.sttFragment to it.correctedExercise }
        }
    }

    data class PendingLog(
        val rawText: String,
        val parsed: ParsedWorkout,
        val topMatches: List<ExerciseMatch>,
        val restSeconds: Int?,
    )

    sealed class UiState {
        object Idle : UiState()

        object Recording : UiState()

        data class Processing(val status: String) : UiState()

        data class Error(val message: String) : UiState()
    }

    fun startRecording() {
        if (_uiState.value !is UiState.Idle) return
        viewModelScope.launch {
            _uiState.value = UiState.Recording
            runCatching {
                val raw = stt.transcribe()
                if (raw.isBlank()) {
                    _uiState.value = UiState.Idle
                    return@launch
                }
                val text = GemmaInference.normalize(raw, userCorrections)
                _uiState.value = UiState.Processing("Parsing: $text")
                gemma.initialize()
                val llmOutput = gemma.extractWorkoutInfo(text)
                gemma.close()
                Log.e("GymVoice", "Gemma output: \"$llmOutput\"")
                val parsed =
                    WorkoutParser.parse(llmOutput, text)
                        ?: error("Could not parse: \"$text\"")
                val now = System.currentTimeMillis()
                matchAndSave(parsed, now, inferRestSeconds(parsed, now))
            }.onFailure { e ->
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun inferRestSeconds(
        parsed: ParsedWorkout,
        now: Long,
    ): Int? {
        val prev = dao.getLatestTimestamp()
        val gapSeconds = if (prev != null) ((now - prev) / 1000).toInt() else null
        return parsed.restSeconds ?: gapSeconds?.takeIf { it in 1..MAX_INFERRED_REST }
    }

    private suspend fun matchAndSave(
        parsed: ParsedWorkout,
        now: Long,
        restSeconds: Int?,
    ) {
        val exercises = allExercisesDeferred.await() + extraExercises
        val matches =
            if (exercises.isNotEmpty()) {
                ExerciseMatcher.findTopMatches(parsed.exercise, exercises)
            } else {
                emptyList()
            }
        val topMatch = matches.firstOrNull()
        val isHighConf = topMatch != null && ExerciseMatcher.isHighConfidence(parsed.exercise, topMatch)

        if (matches.isEmpty() || isHighConf) {
            val exerciseName = topMatch?.exercise?.name ?: parsed.exercise
            val log =
                WorkoutLog(
                    sessionId = UUID.randomUUID().toString(),
                    exerciseName = exerciseName,
                    setNumber = parsed.set,
                    reps = parsed.reps,
                    weight = parsed.weight,
                    unit = parsed.unit,
                    restSeconds = restSeconds,
                    timestamp = now,
                )
            _lastLogged.emit(log.copy(id = dao.insert(log)))
            _uiState.value = UiState.Idle
        } else {
            _pendingLog.value =
                PendingLog(
                    rawText = parsed.exercise,
                    parsed = parsed,
                    topMatches = matches,
                    restSeconds = restSeconds,
                )
            _pendingConfirmation.emit(Unit)
            _uiState.value = UiState.Idle
        }
    }

    suspend fun exerciseList(): List<Exercise> = exerciseDao.getAllList() + extraExercises

    suspend fun updateExercise(exercise: Exercise): Exercise {
        exerciseDao.update(exercise)
        val idx = extraExercises.indexOfFirst { it.id == exercise.id }
        if (idx >= 0) extraExercises[idx] = exercise
        return exercise
    }

    suspend fun createExercise(
        name: String,
        muscleGroup: String,
        equipment: String,
        imagePath: String = "",
    ): Exercise {
        val exercise =
            Exercise(
                name = name.trim(),
                muscleGroup = muscleGroup.trim(),
                equipment = equipment.trim(),
                level = "beginner",
                imageName = imagePath,
            )
        val id = exerciseDao.insert(exercise)
        val saved = exercise.copy(id = id)
        extraExercises.add(saved)
        return saved
    }

    suspend fun exerciseExists(name: String): Boolean = exerciseDao.countByName(name.trim()) > 0

    suspend fun distinctLogNames(): List<String> = dao.getDistinctExerciseNamesOnce()

    fun applyRenames(renames: Map<String, String>) =
        viewModelScope.launch {
            val applied = mutableListOf<String>()
            for ((old, new) in renames) {
                val rows = dao.renameExercise(old, new)
                applied.add("$old → $new ($rows rows)")
            }
            _standardizeResult.emit(
                "RENAMED (${applied.size}):\n" + applied.joinToString("\n") { "• $it" },
            )
        }

    fun standardizeLogs() =
        viewModelScope.launch {
            val exercises = allExercisesDeferred.await()
            if (exercises.isEmpty()) {
                _standardizeResult.emit("Exercise DB empty — cannot standardize")
                return@launch
            }
            val names = dao.getDistinctExerciseNamesOnce()
            if (names.isEmpty()) {
                _standardizeResult.emit("No logs found")
                return@launch
            }
            val renames = mutableListOf<String>()
            val skipped = mutableListOf<String>()
            for (name in names) {
                val matches = ExerciseMatcher.findTopMatches(name, exercises, 1)
                val top = matches.firstOrNull()
                val tokens = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                when {
                    top == null -> skipped.add("$name (no match)")
                    tokens.size <= 1 -> skipped.add("$name → ${top.exercise.name} (single word, skipped)")
                    top.confidence < 0.6f -> skipped.add("$name → ${top.exercise.name} (low conf ${top.confidence})")
                    top.exercise.name == name -> skipped.add("$name (already canonical)")
                    else -> {
                        val rows = dao.renameExercise(name, top.exercise.name)
                        renames.add("$name → ${top.exercise.name} ($rows rows)")
                    }
                }
            }
            val sb = StringBuilder()
            if (renames.isNotEmpty()) {
                sb.append("RENAMED (${renames.size}):\n")
                renames.forEach { sb.append("• $it\n") }
            }
            if (skipped.isNotEmpty()) {
                if (renames.isNotEmpty()) sb.append("\n")
                sb.append("SKIPPED (${skipped.size}):\n")
                skipped.forEach { sb.append("• $it\n") }
            }
            _standardizeResult.emit(sb.toString().trim())
        }

    val exerciseImageMap: StateFlow<Map<String, String>> =
        exerciseDao.getAll()
            .map { list -> list.associate { it.name.lowercase() to it.imageName } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun stopRecording() = stt.stop()

    @Suppress("LongParameterList")
    fun logManual(
        exercise: String,
        sets: Int?,
        reps: Int?,
        weight: Float?,
        unit: String,
        restSeconds: Int? = null,
    ) = viewModelScope.launch {
        if (exercise.isBlank()) return@launch
        dao.insert(
            WorkoutLog(
                sessionId = UUID.randomUUID().toString(),
                exerciseName = exercise.lowercase().trim(),
                setNumber = sets,
                reps = reps,
                weight = weight,
                unit = unit,
                restSeconds = restSeconds,
            ),
        )
    }

    fun saveCorrection(
        original: String,
        corrected: String,
    ) = viewModelScope.launch {
        if (original.isBlank() || corrected.isBlank() || original == corrected) return@launch
        val key = original.lowercase().trim()
        val value = corrected.lowercase().trim()
        correctionDao.upsert(UserCorrection(sttFragment = key, correctedExercise = value))
        userCorrections = userCorrections + (key to value)
    }

    fun cloneLog(log: WorkoutLog) =
        viewModelScope.launch {
            val newId =
                dao.insert(
                    log.copy(id = 0, sessionId = UUID.randomUUID().toString(), timestamp = System.currentTimeMillis()),
                )
            _cloneEvent.emit(log.copy(id = newId))
        }

    fun updateLog(log: WorkoutLog) = viewModelScope.launch { dao.update(log) }

    fun deleteLog(log: WorkoutLog) = viewModelScope.launch { dao.delete(log) }

    fun confirmLog(
        exercise: Exercise,
        pending: PendingLog,
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val log =
            WorkoutLog(
                sessionId = UUID.randomUUID().toString(),
                exerciseName = exercise.name,
                setNumber = pending.parsed.set,
                reps = pending.parsed.reps,
                weight = pending.parsed.weight,
                unit = pending.parsed.unit,
                restSeconds = pending.restSeconds,
                timestamp = now,
            )
        _pendingLog.value = null
        _lastLogged.emit(log.copy(id = dao.insert(log)))
    }

    override fun onCleared() {
        super.onCleared()
        stt.close()
        gemma.close()
    }

    private fun startOfToday(): Long =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
