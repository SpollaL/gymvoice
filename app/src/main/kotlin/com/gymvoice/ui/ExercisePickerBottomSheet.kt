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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.gymvoice.R
import com.gymvoice.data.Exercise
import com.gymvoice.databinding.BottomSheetExercisePickerBinding
import kotlinx.coroutines.launch

class ExercisePickerBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetExercisePickerBinding? = null
    val binding get() = _binding!!

    var onPicked: ((Exercise) -> Unit)? = null

    private val vm: MainViewModel by activityViewModels()
    private var allExercises: List<Exercise> = emptyList()
    private var selectedMuscle: String? = null
    private var selectedEquipment: String? = null

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
            val all = vm.exerciseList()
            val recentNames = vm.recentExerciseNames()
            val recentNameSet = recentNames.toSet()
            val recent = recentNames.mapNotNull { name -> all.find { it.name == name } }
            val rest = all.filter { it.name !in recentNameSet }
            allExercises = recent + rest
            applyFilters()

            val muscles = vm.muscleGroups()
            val equipment = vm.equipmentList()
            buildChips(muscles, binding.cgPickerMuscle) {
                selectedMuscle = it
                applyFilters()
            }
            buildChips(equipment, binding.cgPickerEquipment) {
                selectedEquipment = it
                applyFilters()
            }
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
                    applyFilters()
                }
            },
        )

        binding.btnAddExercise.setOnClickListener {
            showAddDialog(binding.etPickerSearch.text?.toString()?.trim() ?: "")
        }
    }

    private fun applyFilters() {
        val query = binding.etPickerSearch.text?.toString()?.trim() ?: ""
        val filtered =
            allExercises.filter { ex ->
                (selectedMuscle == null || ex.muscleGroup == selectedMuscle) &&
                    (selectedEquipment == null || ex.equipment == selectedEquipment) &&
                    (query.length < 2 || ex.name.contains(query, ignoreCase = true))
            }
        adapter.submitList(filtered)
        binding.btnAddExercise.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildChips(
        items: List<String>,
        chipGroup: ChipGroup,
        onFilter: (String?) -> Unit,
    ) {
        val allChip =
            Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(R.string.chip_all)
                isCheckable = true
                isChecked = true
            }
        chipGroup.addView(allChip)

        items.forEach { item ->
            chipGroup.addView(
                Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = item
                    isCheckable = true
                    tag = item
                },
            )
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val chip = checkedIds.firstOrNull()?.let { group.findViewById<Chip>(it) }
            onFilter(chip?.tag as? String)
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
