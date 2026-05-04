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
                    log.weight?.let { append("$it ${log.unit}  ") }
                    log.restSeconds?.let { s ->
                        append("rest ")
                        if (s >= 60) {
                            val m = s / 60
                            val rem = s % 60
                            if (rem > 0) append("${m}m${rem}s") else append("${m}m")
                        } else {
                            append("${s}s")
                        }
                    }
                }.trim()
            binding.root.setOnClickListener { onEdit(log) }
            binding.root.setOnLongClickListener(null)
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
