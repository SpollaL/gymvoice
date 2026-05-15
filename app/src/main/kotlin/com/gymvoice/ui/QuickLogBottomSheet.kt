package com.gymvoice.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gymvoice.databinding.BottomSheetQuickLogBinding

class QuickLogBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetQuickLogBinding? = null
    val binding get() = _binding!!
    private val mainVm: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetQuickLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val exerciseName = requireArguments().getString(ARG_EXERCISE_NAME) ?: return
        binding.tvExerciseName.text = exerciseName

        binding.btnLog.setOnClickListener {
            val sets = binding.etSets.text?.toString()?.toIntOrNull()
            val reps = binding.etReps.text?.toString()?.toIntOrNull()
            val weight = binding.etWeight.text?.toString()?.toFloatOrNull()
            val unit = if (binding.rbLbs.isChecked) "lbs" else "kg"
            mainVm.logManual(exerciseName, sets, reps, weight, unit)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_EXERCISE_NAME = "exercise_name"

        fun newInstance(exerciseName: String): QuickLogBottomSheet =
            QuickLogBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_EXERCISE_NAME, exerciseName) }
            }
    }
}
