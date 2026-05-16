package com.gymvoice.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gymvoice.databinding.BottomSheetRenameLogsBinding
import kotlinx.coroutines.launch

class RenameLogsBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetRenameLogsBinding? = null
    val binding get() = _binding!!

    private val vm: MainViewModel by activityViewModels()
    private lateinit var adapter: RenameLogsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetRenameLogsBinding.inflate(inflater, container, false)
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

        adapter =
            RenameLogsAdapter(
                onPickClick = { position, _ ->
                    ExercisePickerBottomSheet().also { picker ->
                        picker.onPicked = { exercise -> adapter.setTarget(position, exercise.name) }
                    }.show(childFragmentManager, "picker_rename")
                },
                onChanged = { updateApplyButton() },
            )

        binding.rvRenames.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRenames.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            val names = vm.distinctLogNames()
            adapter.submitList(names.map { RenameItem(it) })
        }

        binding.btnApplyRenames.setOnClickListener {
            val renames = adapter.pendingRenames()
            if (renames.isNotEmpty()) vm.applyRenames(renames)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateApplyButton() {
        val count = adapter.renameCount()
        binding.btnApplyRenames.text = "Apply $count rename${if (count == 1) "" else "s"}"
    }
}
