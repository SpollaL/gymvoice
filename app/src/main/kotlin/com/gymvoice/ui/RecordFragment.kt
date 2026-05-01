package com.gymvoice.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.gymvoice.R
import com.gymvoice.data.WorkoutLog
import com.gymvoice.databinding.DialogEditLogBinding
import com.gymvoice.databinding.FragmentRecordBinding
import kotlinx.coroutines.launch

class RecordFragment : Fragment() {
    private var _binding: FragmentRecordBinding? = null
    val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: WorkoutLogAdapter

    private val requestMic =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(requireContext(), "Microphone permission required", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        adapter = WorkoutLogAdapter(onEdit = ::showEditDialog)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnRecord.setOnClickListener {
            when (viewModel.uiState.value) {
                is MainViewModel.UiState.Idle -> viewModel.startRecording()
                is MainViewModel.UiState.Recording -> viewModel.stopRecording()
                else -> Unit
            }
        }

        binding.btnManualEntry.setOnClickListener { showManualEntryDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is MainViewModel.UiState.Idle -> {
                            binding.btnRecord.text = getString(R.string.tap_to_record)
                            binding.btnRecord.alpha = 1f
                            binding.tvStatus.text = ""
                        }
                        is MainViewModel.UiState.Recording -> {
                            binding.btnRecord.text = getString(R.string.tap_to_stop)
                            binding.btnRecord.alpha = 1f
                            binding.tvStatus.text = ""
                        }
                        is MainViewModel.UiState.Processing -> {
                            binding.btnRecord.alpha = 0.5f
                            binding.tvStatus.text = state.status
                        }
                        is MainViewModel.UiState.Error -> {
                            binding.btnRecord.text = getString(R.string.tap_to_record)
                            binding.btnRecord.alpha = 1f
                            binding.tvStatus.text = state.message
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.todayLogs.collect { adapter.submitList(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lastLogged.collect { log ->
                    Snackbar.make(binding.root, "Logged: ${log.exerciseName}", Snackbar.LENGTH_LONG)
                        .setAction("Fix") { showFixDialog(log) }
                        .show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cloneEvent.collect { cloned ->
                    Snackbar.make(binding.root, "Cloned ${cloned.exerciseName}", Snackbar.LENGTH_LONG)
                        .setAction("Undo") { viewModel.deleteLog(cloned) }
                        .show()
                }
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showManualEntryDialog() {
        val dialogBinding = DialogEditLogBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle("Log manually")
            .setView(dialogBinding.root)
            .setPositiveButton("Log") { _, _ ->
                val exercise = dialogBinding.etExercise.text.toString().trim()
                if (exercise.isNotBlank()) {
                    val unit = if (dialogBinding.rgUnit.checkedRadioButtonId == R.id.rbLbs) "lbs" else "kg"
                    viewModel.logManual(
                        exercise = exercise,
                        sets = dialogBinding.etSet.text.toString().toIntOrNull(),
                        reps = dialogBinding.etReps.text.toString().toIntOrNull(),
                        weight = dialogBinding.etWeight.text.toString().toFloatOrNull(),
                        unit = unit,
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFixDialog(log: WorkoutLog) {
        val input =
            EditText(requireContext()).apply {
                setText(log.exerciseName)
                selectAll()
                setPadding(48, 24, 48, 24)
            }
        AlertDialog.Builder(requireContext())
            .setTitle("Correct exercise name")
            .setMessage("What did you say?")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val corrected = input.text.toString().trim()
                if (corrected.isNotBlank() && corrected != log.exerciseName) {
                    viewModel.saveCorrection(log.exerciseName, corrected)
                    viewModel.updateLog(log.copy(exerciseName = corrected))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(log: WorkoutLog) {
        val dialogBinding = DialogEditLogBinding.inflate(layoutInflater)
        dialogBinding.etExercise.setText(log.exerciseName)
        dialogBinding.etSet.setText(log.setNumber?.toString() ?: "")
        dialogBinding.etReps.setText(log.reps?.toString() ?: "")
        dialogBinding.etWeight.setText(log.weight?.toString() ?: "")
        if (log.unit == "lbs") dialogBinding.rbLbs.isChecked = true else dialogBinding.rbKg.isChecked = true

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Log")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val newExercise = dialogBinding.etExercise.text.toString()
                if (newExercise != log.exerciseName) {
                    viewModel.saveCorrection(log.exerciseName, newExercise)
                }
                val unit = if (dialogBinding.rgUnit.checkedRadioButtonId == R.id.rbLbs) "lbs" else "kg"
                viewModel.updateLog(
                    log.copy(
                        exerciseName = newExercise,
                        setNumber = dialogBinding.etSet.text.toString().toIntOrNull(),
                        reps = dialogBinding.etReps.text.toString().toIntOrNull(),
                        weight = dialogBinding.etWeight.text.toString().toFloatOrNull(),
                        unit = unit,
                    ),
                )
            }
            .setNeutralButton("Clone") { _, _ -> viewModel.cloneLog(log) }
            .setNegativeButton("Delete") { _, _ -> viewModel.deleteLog(log) }
            .show()
    }
}
