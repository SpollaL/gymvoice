package com.gymvoice.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.audio.SpeechRecognizerSTT
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.UserCorrection
import com.gymvoice.data.WorkoutLog
import com.gymvoice.ml.GemmaInference
import com.gymvoice.ml.WorkoutParser
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).workoutLogDao()
    private val correctionDao = AppDatabase.getInstance(app).userCorrectionDao()
    private val stt = SpeechRecognizerSTT(app)
    private val gemma = GemmaInference()

    private var userCorrections = mapOf<String, String>()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _lastLogged = MutableSharedFlow<WorkoutLog>(extraBufferCapacity = 1)
    val lastLogged: SharedFlow<WorkoutLog> = _lastLogged.asSharedFlow()

    val todayLogs = dao.getTodayLogs(startOfToday())

    init {
        viewModelScope.launch {
            userCorrections = correctionDao.getAll().associate { it.sttFragment to it.correctedExercise }
        }
    }

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
                val text = GemmaInference.normalize(raw, userCorrections)

                _uiState.value = UiState.Processing("Parsing: $text")
                gemma.initialize()
                val llmOutput = gemma.extractWorkoutInfo(text)
                gemma.close()
                Log.e("GymVoice", "Gemma output: \"$llmOutput\"")

                val parsed =
                    WorkoutParser.parse(llmOutput, text)
                        ?: error("Could not parse: \"$text\"")

                val log =
                    WorkoutLog(
                        sessionId = UUID.randomUUID().toString(),
                        exerciseName = parsed.exercise,
                        setNumber = parsed.set,
                        reps = parsed.reps,
                        weight = parsed.weight,
                        unit = parsed.unit,
                    )
                val logId = dao.insert(log)
                _lastLogged.emit(log.copy(id = logId))
                _uiState.value = UiState.Idle
            }.onFailure { e ->
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun stopRecording() = stt.stop()

    fun logManual(
        exercise: String,
        sets: Int?,
        reps: Int?,
        weight: Float?,
        unit: String,
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

    fun updateLog(log: WorkoutLog) = viewModelScope.launch { dao.update(log) }

    fun deleteLog(log: WorkoutLog) = viewModelScope.launch { dao.delete(log) }

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
