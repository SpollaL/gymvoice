package com.gymvoice.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.ExportFormat
import com.gymvoice.data.ExportRepository
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
        if (_uiState.value is UiState.Loading) return
        val capturedFromMs = fromMs
        val capturedToMs = toMs
        val capturedFormat = format
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = when (val result = repository.export(capturedFormat, capturedFromMs, capturedToMs)) {
                is ExportRepository.ExportResult.Success -> UiState.Success(result.uri)
                is ExportRepository.ExportResult.Failure -> UiState.Error(result.message)
            }
        }
    }

    fun resetState() {
        _uiState.value = UiState.Idle
    }
}
