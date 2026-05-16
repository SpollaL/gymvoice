package com.gymvoice.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymvoice.R
import com.gymvoice.databinding.ItemExerciseMatchBinding
import com.gymvoice.ml.ExerciseMatch

class ConfirmMatchAdapter : ListAdapter<ExerciseMatch, ConfirmMatchAdapter.VH>(DIFF) {
    var selectedPosition: Int = 0
    val selectedMatch: ExerciseMatch? get() = currentList.getOrNull(selectedPosition)

    inner class VH(val binding: ItemExerciseMatchBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val prev = selectedPosition
                selectedPosition = bindingAdapterPosition
                notifyItemChanged(prev)
                notifyItemChanged(selectedPosition)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ) = VH(ItemExerciseMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: VH,
        position: Int,
    ) {
        val match = getItem(position)
        val ex = match.exercise
        holder.binding.tvMatchName.text = ex.name
        holder.binding.tvMatchEquipment.text = ex.equipment

        holder.binding.ivThumb.loadExerciseImage(ex.imageName)

        val strokeColor =
            if (position == selectedPosition) {
                ContextCompat.getColor(holder.itemView.context, R.color.mauve)
            } else {
                ContextCompat.getColor(holder.itemView.context, R.color.surface1)
            }
        holder.binding.cardMatch.strokeColor = strokeColor
    }

    companion object {
        val DIFF =
            object : DiffUtil.ItemCallback<ExerciseMatch>() {
                override fun areItemsTheSame(
                    a: ExerciseMatch,
                    b: ExerciseMatch,
                ) = a.exercise.id == b.exercise.id

                override fun areContentsTheSame(
                    a: ExerciseMatch,
                    b: ExerciseMatch,
                ) = a == b
            }
    }
}
