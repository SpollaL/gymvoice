package com.gymvoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.gymvoice.data.WorkoutLog
import com.gymvoice.databinding.ActivityMainBinding
import com.gymvoice.databinding.DialogEditLogBinding
import com.gymvoice.ui.MainViewModel
import com.gymvoice.ui.WorkoutLogAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: WorkoutLogAdapter

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = WorkoutLogAdapter(onEdit = ::showEditDialog)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnRecord.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> viewModel.startRecording()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> viewModel.stopRecording()
            }
            true
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is MainViewModel.UiState.Idle -> {
                            binding.btnRecord.text = getString(R.string.hold_to_record)
                            binding.tvStatus.text = ""
                        }
                        is MainViewModel.UiState.Recording -> {
                            binding.btnRecord.text = "Recording..."
                            binding.tvStatus.text = "Listening..."
                        }
                        is MainViewModel.UiState.Processing -> binding.tvStatus.text = state.status
                        is MainViewModel.UiState.Error -> {
                            binding.tvStatus.text = "Error: ${state.message}"
                            binding.btnRecord.text = getString(R.string.hold_to_record)
                        }
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.todayLogs.collect { adapter.submitList(it) }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun showEditDialog(log: WorkoutLog) {
        val dialogBinding = DialogEditLogBinding.inflate(layoutInflater)
        dialogBinding.etExercise.setText(log.exerciseName)
        dialogBinding.etSet.setText(log.setNumber?.toString() ?: "")
        dialogBinding.etReps.setText(log.reps?.toString() ?: "")
        dialogBinding.etWeight.setText(log.weight?.toString() ?: "")

        AlertDialog.Builder(this)
            .setTitle("Edit Log")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                viewModel.updateLog(
                    log.copy(
                        exerciseName = dialogBinding.etExercise.text.toString(),
                        setNumber = dialogBinding.etSet.text.toString().toIntOrNull(),
                        reps = dialogBinding.etReps.text.toString().toIntOrNull(),
                        weight = dialogBinding.etWeight.text.toString().toFloatOrNull(),
                    ),
                )
            }
            .setNegativeButton("Delete") { _, _ -> viewModel.deleteLog(log) }
            .show()
    }
}
