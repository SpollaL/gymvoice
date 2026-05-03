package com.gymvoice.ui

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.gymvoice.R
import com.gymvoice.data.ExportFormat
import com.gymvoice.databinding.DialogExportBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExportDialogFragment : DialogFragment() {
    private lateinit var binding: DialogExportBinding
    private val viewModel: ExportViewModel by viewModels()

    private var fromMs = 0L
    private var toMs = Long.MAX_VALUE
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogExportBinding.inflate(layoutInflater)

        binding.tvFromDate.text = getString(R.string.export_all_time)
        binding.tvToDate.text = dateFmt.format(Date(System.currentTimeMillis()))

        binding.tvFromDate.setOnClickListener { pickFromDate() }
        binding.tvToDate.setOnClickListener { pickToDate() }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.export_title)
            .setView(binding.root)
            .setPositiveButton(R.string.export_button, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val exportBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            exportBtn.setOnClickListener { triggerExport() }
            observeState(dialog, exportBtn)
        }

        return dialog
    }

    private fun observeState(dialog: AlertDialog, exportBtn: Button) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is ExportViewModel.UiState.Loading -> {
                            exportBtn.isEnabled = false
                            exportBtn.text = getString(R.string.export_exporting)
                        }
                        is ExportViewModel.UiState.Success -> {
                            Toast.makeText(
                                requireContext(),
                                R.string.export_saved,
                                Toast.LENGTH_SHORT,
                            ).show()
                            shareFile(state.uri)
                            dialog.dismiss()
                            viewModel.resetState()
                        }
                        is ExportViewModel.UiState.Error -> {
                            exportBtn.isEnabled = true
                            exportBtn.text = getString(R.string.export_button)
                            Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetState()
                        }
                        ExportViewModel.UiState.Idle -> {
                            exportBtn.isEnabled = true
                            exportBtn.text = getString(R.string.export_button)
                        }
                    }
                }
            }
        }
    }

    private fun triggerExport() {
        viewModel.fromMs = fromMs
        viewModel.toMs = toMs
        viewModel.format = if (binding.chipExcel.isChecked) ExportFormat.XLSX else ExportFormat.PDF
        viewModel.export()
    }

    private fun pickFromDate() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (fromMs == 0L) System.currentTimeMillis() else fromMs
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                fromMs = Calendar.getInstance().apply {
                    set(year, month, day, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                binding.tvFromDate.text = dateFmt.format(Date(fromMs))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun pickToDate() {
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (toMs == Long.MAX_VALUE) System.currentTimeMillis() else toMs
        }
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                toMs = Calendar.getInstance().apply {
                    set(year, month, day, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                binding.tvToDate.text = dateFmt.format(Date(toMs))
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun shareFile(uri: Uri) {
        val mimeType = if (binding.chipExcel.isChecked) {
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        } else {
            "application/pdf"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.export_share_title)))
    }
}
