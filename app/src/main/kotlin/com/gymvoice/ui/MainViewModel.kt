package com.gymvoice.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.audio.SpeechRecognizerSTT
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.WorkoutLog
import com.gymvoice.ml.GemmaInference
import com.gymvoice.ml.WorkoutParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).workoutLogDao()
    private val stt = SpeechRecognizerSTT(app)
    private val gemma = GemmaInference()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val todayLogs = dao.getTodayLogs(startOfToday())

    sealed class UiState {
        object Idle : UiState()

        object Recording : UiState()

        data class Processing(val status: String) : UiState()

        data class Error(val message: String) : UiState()
    }

    fun startRecording() {
        viewModelScope.launch {
            _uiState.value = UiState.Recording
            runCatching {
                val text = stt.transcribe()

                _uiState.value = UiState.Processing("Parsing: $text")
                gemma.initialize()
                val llmOutput = gemma.extractWorkoutInfo(text)
                gemma.close()
                Log.e("GymVoice", "Gemma output: \"$llmOutput\"")

                val parsed =
                    WorkoutParser.parse(llmOutput, text)
                        ?: error("Could not parse: \"$text\"")

                dao.insert(
                    WorkoutLog(
                        sessionId = UUID.randomUUID().toString(),
                        exerciseName = parsed.exercise,
                        setNumber = parsed.set,
                        reps = parsed.reps,
                        weight = parsed.weight,
                        unit = parsed.unit,
                    ),
                )
                _uiState.value = UiState.Idle
            }.onFailure { e ->
                _uiState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun stopRecording() = stt.stop()

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
