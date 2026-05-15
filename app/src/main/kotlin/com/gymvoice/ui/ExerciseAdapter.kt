package com.gymvoice.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gymvoice.data.Exercise
import com.gymvoice.databinding.ItemExerciseBinding

class ExerciseAdapter(
    private val onClick: (Exercise) -> Unit,
) : ListAdapter<Exercise, ExerciseAdapter.ViewHolder>(DIFF) {
    inner class ViewHolder(private val binding: ItemExerciseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(exercise: Exercise) {
            binding.tvName.text = exercise.name
            binding.tvEquipment.text = exercise.equipment
            binding.ivExercise.load(Uri.parse("file:///android_asset/exercises/${exercise.imageName}")) {
                crossfade(true)
            }
            binding.root.setOnClickListener { onClick(exercise) }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(ItemExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) = holder.bind(getItem(position))

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<Exercise>() {
                override fun areItemsTheSame(
                    oldItem: Exercise,
                    newItem: Exercise,
                ) = oldItem.id == newItem.id

                override fun areContentsTheSame(
                    oldItem: Exercise,
                    newItem: Exercise,
                ) = oldItem == newItem
            }
    }
}
