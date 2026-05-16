package com.gymvoice.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.chip.Chip
import com.gymvoice.R
import com.gymvoice.databinding.FragmentLibraryBinding
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {
    private var _binding: FragmentLibraryBinding? = null
    val binding get() = _binding!!
    private val vm: LibraryViewModel by viewModels()

    private val adapter =
        ExerciseAdapter(
            onClick = { exercise ->
                QuickLogBottomSheet.newInstance(exercise.name)
                    .show(childFragmentManager, "quick_log")
            },
            onLongClick = { exercise ->
                AddExerciseBottomSheet().also { it.existingExercise = exercise }
                    .show(childFragmentManager, "edit_exercise")
            },
        )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvExercises.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvExercises.adapter = adapter

        binding.etSearch.addTextChangedListener(
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
                    vm.setSearchQuery(s?.toString() ?: "")
                }
            },
        )

        binding.fabAddExercise.setOnClickListener {
            AddExerciseBottomSheet().show(childFragmentManager, "add_exercise")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.muscleGroups.collect { groups -> buildFilterChips(groups, binding.cgMuscle) { vm.setMuscleGroup(it) } }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.equipmentList.collect {
                    items ->
                buildFilterChips(items, binding.cgEquipment) { vm.setEquipmentFilter(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.exercises.collect { exercises ->
                adapter.submitList(exercises)
                binding.tvEmpty.isVisible = exercises.isEmpty()
                binding.rvExercises.isVisible = exercises.isNotEmpty()
            }
        }
    }

    private fun buildFilterChips(
        items: List<String>,
        chipGroup: com.google.android.material.chip.ChipGroup,
        onFilter: (String?) -> Unit,
    ) {
        if (chipGroup.childCount > 0) return

        val allChip =
            Chip(requireContext()).apply {
                id = View.generateViewId()
                text = getString(R.string.chip_all)
                isCheckable = true
                isChecked = true
            }
        chipGroup.addView(allChip)

        items.forEach { item ->
            val chip =
                Chip(requireContext()).apply {
                    id = View.generateViewId()
                    text = item
                    isCheckable = true
                    tag = item
                }
            chipGroup.addView(chip)
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
}
