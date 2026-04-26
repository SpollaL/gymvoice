package com.gymvoice.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymvoice.data.WorkoutLog
import com.gymvoice.databinding.ItemWorkoutLogBinding

class WorkoutLogAdapter(
    private val onEdit: (WorkoutLog) -> Unit,
) : ListAdapter<WorkoutLog, WorkoutLogAdapter.ViewHolder>(DIFF) {
    inner class ViewHolder(private val binding: ItemWorkoutLogBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(log: WorkoutLog) {
            binding.tvExercise.text = log.exerciseName
            binding.tvDetails.text =
                buildString {
                    log.setNumber?.let { append("Set $it  ") }
                    log.reps?.let { append("$it reps  ") }
                    log.weight?.let { append("$it ${log.unit}") }
                }.trim()
            binding.root.setOnClickListener { onEdit(log) }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ) = ViewHolder(
        ItemWorkoutLogBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) = holder.bind(getItem(position))

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<WorkoutLog>() {
                override fun areItemsTheSame(
                    a: WorkoutLog,
                    b: WorkoutLog,
                ) = a.id == b.id

                override fun areContentsTheSame(
                    a: WorkoutLog,
                    b: WorkoutLog,
                ) = a == b
            }
    }
}
