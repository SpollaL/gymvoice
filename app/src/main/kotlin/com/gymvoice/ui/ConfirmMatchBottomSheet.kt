package com.gymvoice.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gymvoice.databinding.BottomSheetConfirmMatchBinding

class ConfirmMatchBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetConfirmMatchBinding? = null
    val binding get() = _binding!!

    private val vm: MainViewModel by activityViewModels()
    private val adapter = ConfirmMatchAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetConfirmMatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val pending =
            vm.pendingLog.value ?: run {
                dismiss()
                return
            }

        binding.tvHeard.text = "heard: ${pending.rawText}"
        binding.rvMatches.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMatches.adapter = adapter
        adapter.submitList(pending.topMatches)

        binding.btnConfirmLog.setOnClickListener {
            val selected = adapter.selectedMatch ?: return@setOnClickListener
            vm.confirmLog(selected.exercise, pending)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
