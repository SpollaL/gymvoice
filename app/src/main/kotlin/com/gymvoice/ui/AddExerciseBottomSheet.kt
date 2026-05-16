package com.gymvoice.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gymvoice.data.Exercise
import com.gymvoice.databinding.BottomSheetAddExerciseBinding
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class AddExerciseBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomSheetAddExerciseBinding? = null
    val binding get() = _binding!!

    var initialName: String = ""
    var existingExercise: Exercise? = null
    var onCreated: ((Exercise) -> Unit)? = null

    private val vm: MainViewModel by activityViewModels()
    private var photoPath: String = ""

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri ?: return@registerForActivityResult
            val dest = File(requireContext().filesDir, "exercise_images/${UUID.randomUUID()}.jpg")
            dest.parentFile?.mkdirs()
            requireContext().contentResolver.openInputStream(uri)?.use { it.copyTo(dest.outputStream()) }
            photoPath = dest.absolutePath
            binding.ivExercisePhoto.load(dest) { crossfade(true) }
            binding.ivExercisePhoto.visibility = View.VISIBLE
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetAddExerciseBinding.inflate(inflater, container, false)
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
        val ex = existingExercise
        if (ex != null) {
            binding.etName.setText(ex.name)
            setupDropdowns()
            binding.actvMuscle.setText(ex.muscleGroup, false)
            binding.actvEquipment.setText(ex.equipment, false)
            if (ex.imageName.isNotEmpty()) {
                binding.ivExercisePhoto.loadExerciseImage(ex.imageName)
                binding.ivExercisePhoto.visibility = View.VISIBLE
                photoPath = ex.imageName
            }
        } else {
            binding.etName.setText(toTitleCase(initialName))
            setupDropdowns()
        }
        binding.btnPickPhoto.setOnClickListener { pickPhoto.launch("image/*") }
        binding.btnSaveExercise.setOnClickListener { save() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupDropdowns() {
        val muscleGroups =
            listOf(
                "Abs",
                "Back",
                "Biceps",
                "Calves",
                "Chest",
                "Core",
                "Forearms",
                "Full Body",
                "Glutes",
                "Hamstrings",
                "Legs",
                "Quadriceps",
                "Shoulders",
                "Triceps",
            )
        val equipment =
            listOf(
                "bands",
                "barbell",
                "body only",
                "cable",
                "dumbbell",
                "ezbar",
                "kettlebells",
                "machine",
                "medicine ball",
                "other",
            )
        binding.actvMuscle.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, muscleGroups),
        )
        binding.actvEquipment.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, equipment),
        )
    }

    private fun save() {
        val name = toTitleCase(binding.etName.text.toString().trim())
        if (name.isBlank()) return
        val existing = existingExercise
        viewLifecycleOwner.lifecycleScope.launch {
            if (existing == null && vm.exerciseExists(name)) {
                binding.tilName.error = "Already exists"
                return@launch
            }
            binding.tilName.error = null
            val exercise =
                if (existing != null) {
                    vm.updateExercise(
                        existing.copy(
                            name = name,
                            muscleGroup = binding.actvMuscle.text.toString(),
                            equipment = binding.actvEquipment.text.toString(),
                            imageName = if (photoPath.isNotEmpty()) photoPath else existing.imageName,
                        ),
                    )
                } else {
                    vm.createExercise(
                        name = name,
                        muscleGroup = binding.actvMuscle.text.toString(),
                        equipment = binding.actvEquipment.text.toString(),
                        imagePath = photoPath,
                    )
                }
            onCreated?.invoke(exercise)
            dismiss()
        }
    }

    private fun toTitleCase(s: String): String =
        s.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
}
