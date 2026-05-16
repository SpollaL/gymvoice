package com.gymvoice.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymvoice.data.Exercise
import com.gymvoice.databinding.ItemExerciseBinding

class ExerciseAdapter(
    private val onClick: (Exercise) -> Unit,
    private val onLongClick: ((Exercise) -> Unit)? = null,
) : ListAdapter<Exercise, ExerciseAdapter.ViewHolder>(DIFF) {
    inner class ViewHolder(private val binding: ItemExerciseBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(exercise: Exercise) {
            binding.tvName.text = exercise.name
            binding.tvEquipment.text = exercise.equipment
            binding.ivExercise.loadExerciseImage(exercise.imageName)
            binding.root.setOnClickListener { onClick(exercise) }
            binding.root.setOnLongClickListener {
                onLongClick?.invoke(exercise)
                onLongClick != null
            }
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
