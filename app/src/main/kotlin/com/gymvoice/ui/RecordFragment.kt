package com.gymvoice.ui

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
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
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: WorkoutLogAdapter
    private var pulseAnimator: AnimatorSet? = null

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

    @Suppress("LongMethod")
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        adapter = WorkoutLogAdapter(onEdit = ::showEditDialog)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exerciseImageMap.collect { adapter.imageMap = it }
            }
        }

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
                            setButtonIdle()
                            binding.tvStatus.text = ""
                        }
                        is MainViewModel.UiState.Recording -> {
                            setButtonRecording()
                            binding.tvStatus.text = ""
                        }
                        is MainViewModel.UiState.Processing -> {
                            binding.ivRecordIcon.alpha = 0.35f
                            binding.tvRecordLabel.text = state.status
                            binding.progressSpinner.visibility = View.VISIBLE
                            stopPulse()
                            binding.tvStatus.text = ""
                        }
                        is MainViewModel.UiState.Error -> {
                            setButtonIdle()
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

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingConfirmation.collect {
                    ConfirmMatchBottomSheet()
                        .show(childFragmentManager, "confirm_match")
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
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDestroyView()
        _binding = null
    }

    private fun setButtonIdle() {
        binding.progressSpinner.visibility = View.GONE
        binding.ivRecordIcon.setImageResource(R.drawable.ic_mic)
        binding.ivRecordIcon.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent))
        binding.ivRecordIcon.alpha = 1f
        binding.tvRecordLabel.text = getString(R.string.record_label_idle)
        stopPulse()
    }

    private fun setButtonRecording() {
        binding.progressSpinner.visibility = View.GONE
        binding.ivRecordIcon.setImageResource(R.drawable.ic_stop)
        binding.ivRecordIcon.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.red))
        binding.ivRecordIcon.alpha = 1f
        binding.tvRecordLabel.text = getString(R.string.record_label_recording)
        startPulse()
    }

    private fun startPulse() {
        pulseAnimator?.cancel()
        val ring = binding.pulseRing
        ring.scaleX = 1f
        ring.scaleY = 1f
        ring.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.red))
        val scaleX = ObjectAnimator.ofFloat(ring, "scaleX", 1f, 1.7f).apply {
            duration = 1100
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        val scaleY = ObjectAnimator.ofFloat(ring, "scaleY", 1f, 1.7f).apply {
            duration = 1100
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        val alpha = ObjectAnimator.ofFloat(ring, "alpha", 0.75f, 0f).apply {
            duration = 1100
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        binding.pulseRing.animate().alpha(0f).scaleX(1f).scaleY(1f).setDuration(200).start()
    }

    private fun showManualEntryDialog() {
        val dialogBinding = DialogEditLogBinding.inflate(layoutInflater)
        var selectedExercise = ""
        dialogBinding.etExercise.setOnClickListener {
            openExercisePicker { ex ->
                selectedExercise = ex.name
                dialogBinding.etExercise.setText(ex.name)
            }
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Log manually")
            .setView(dialogBinding.root)
            .setPositiveButton("Log") { _, _ ->
                if (selectedExercise.isNotBlank()) {
                    val unit = if (dialogBinding.rgUnit.checkedRadioButtonId == R.id.rbLbs) "lbs" else "kg"
                    viewModel.logManual(
                        exercise = selectedExercise,
                        sets = dialogBinding.etSet.text.toString().toIntOrNull(),
                        reps = dialogBinding.etReps.text.toString().toIntOrNull(),
                        weight = dialogBinding.etWeight.text.toString().toFloatOrNull(),
                        unit = unit,
                        restSeconds = dialogBinding.etRest.text.toString().toIntOrNull(),
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openExercisePicker(onPicked: (com.gymvoice.data.Exercise) -> Unit) {
        ExercisePickerBottomSheet().also { it.onPicked = onPicked }
            .show(childFragmentManager, "exercise_picker")
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
        var selectedExercise = log.exerciseName
        dialogBinding.etExercise.setText(log.exerciseName)
        dialogBinding.etSet.setText(log.setNumber?.toString() ?: "")
        dialogBinding.etReps.setText(log.reps?.toString() ?: "")
        dialogBinding.etWeight.setText(log.weight?.toString() ?: "")
        dialogBinding.etRest.setText(log.restSeconds?.toString() ?: "")
        if (log.unit == "lbs") dialogBinding.rbLbs.isChecked = true else dialogBinding.rbKg.isChecked = true
        dialogBinding.etExercise.setOnClickListener {
            openExercisePicker { ex ->
                selectedExercise = ex.name
                dialogBinding.etExercise.setText(ex.name)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Log")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val newExercise = selectedExercise
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
                        restSeconds = dialogBinding.etRest.text.toString().toIntOrNull(),
                    ),
                )
            }
            .setNeutralButton("Clone") { _, _ -> viewModel.cloneLog(log) }
            .setNegativeButton("Delete") { _, _ ->
                viewModel.deleteLog(log)
                Snackbar.make(binding.root, "Deleted ${log.exerciseName}", Snackbar.LENGTH_LONG)
                    .setAction("Undo") { viewModel.insertLog(log) }
                    .show()
            }
            .show()
    }
}
