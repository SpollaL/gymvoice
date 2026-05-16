package com.gymvoice.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gymvoice.data.Exercise
import com.gymvoice.databinding.BottomSheetExercisePickerBinding
import kotlinx.coroutines.launch

class ExercisePickerBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExercisePickerBinding? = null
    val binding get() = _binding!!

    var onPicked: ((Exercise) -> Unit)? = null

    private val vm: MainViewModel by activityViewModels()
    private var allExercises: List<Exercise> = emptyList()

    private val adapter =
        ExercisePickerAdapter { exercise ->
            onPicked?.invoke(exercise)
            dismiss()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetExercisePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvPickerExercises.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPickerExercises.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            allExercises = vm.exerciseList()
            adapter.submitList(allExercises)
        }

        binding.etPickerSearch.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) = Unit

                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString()?.trim() ?: ""
                    adapter.submitList(
                        if (query.isEmpty()) {
                            allExercises
                        } else {
                            allExercises.filter { it.name.contains(query, ignoreCase = true) }
                        },
                    )
                    binding.btnAddExercise.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                }
            },
        )

        binding.btnAddExercise.setOnClickListener {
            showAddDialog(binding.etPickerSearch.text?.toString()?.trim() ?: "")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showAddDialog(name: String) {
        AddExerciseBottomSheet().also { sheet ->
            sheet.initialName = name
            sheet.onCreated = { exercise ->
                allExercises = allExercises + exercise
                onPicked?.invoke(exercise)
                dismiss()
            }
        }.show(childFragmentManager, "add_exercise")
    }
}
