package com.gymvoice.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.gymvoice.R
import com.gymvoice.databinding.FragmentProgressBinding
import kotlinx.coroutines.launch

class ProgressFragment : Fragment() {
    private var _binding: FragmentProgressBinding? = null
    private val binding get() = _binding!!
    private val vm: ProgressViewModel by viewModels()
    private val historyAdapter = ProgressLogAdapter().also { it.onClone = { log -> vm.cloneLog(log) } }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = historyAdapter

        binding.cgMode.setOnCheckedStateChangeListener { _, checkedIds ->
            val mode =
                when (checkedIds.firstOrNull()) {
                    R.id.chipReps -> ProgressMode.REPS
                    R.id.chipVolume -> ProgressMode.VOLUME
                    else -> ProgressMode.WEIGHT
                }
            vm.setMode(mode)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.mode.collect { mode ->
                val chipId =
                    when (mode) {
                        ProgressMode.WEIGHT -> R.id.chipWeight
                        ProgressMode.REPS -> R.id.chipReps
                        ProgressMode.VOLUME -> R.id.chipVolume
                    }
                binding.cgMode.check(chipId)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.exercises.collect { exercises ->
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, exercises)
                binding.actvExercise.setAdapter(adapter)
                if (vm.selectedExercise.value == null && exercises.isNotEmpty()) {
                    vm.selectExercise(exercises[0])
                    binding.actvExercise.setText(exercises[0], false)
                }
            }
        }

        binding.actvExercise.setOnItemClickListener { _, _, position, _ ->
            val adapter = binding.actvExercise.adapter
            if (adapter != null && position < adapter.count) {
                vm.selectExercise(adapter.getItem(position) as String)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.progressData.collect { data ->
                val hasData = data != null
                binding.sparkline.isVisible = hasData
                binding.statsRow.isVisible = hasData
                binding.tvHistoryLabel.isVisible = hasData
                binding.rvHistory.isVisible = hasData
                binding.tvEmpty.isVisible = !hasData

                if (data != null) {
                    binding.sparkline.dataPoints = data.sparklinePoints
                    binding.sparkline.trendUp = data.trendUp

                    binding.tvPrValue.text = data.prLabel
                    binding.tvSessionsValue.text = data.totalSessions.toString()
                    binding.tvTrendValue.text = data.trendLabel
                    binding.tvTrendValue.setTextColor(
                        when (data.trendUp) {
                            true -> resources.getColor(R.color.green, null)
                            false -> resources.getColor(R.color.red, null)
                            null -> resources.getColor(R.color.subtext0, null)
                        },
                    )

                    historyAdapter.prValue = data.prValue
                    historyAdapter.hasWeight = data.hasWeight
                    historyAdapter.mode = data.mode
                    historyAdapter.submitList(data.logs)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.cloneEvent.collect { cloned ->
                    Snackbar.make(binding.root, "Cloned ${cloned.exerciseName}", Snackbar.LENGTH_LONG)
                        .setAction("Undo") { vm.deleteLog(cloned) }
                        .show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
