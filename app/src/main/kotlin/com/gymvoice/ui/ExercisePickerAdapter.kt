package com.gymvoice.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymvoice.data.Exercise
import com.gymvoice.databinding.ItemExerciseMatchBinding

class ExercisePickerAdapter(
    private val onClick: (Exercise) -> Unit,
) : ListAdapter<Exercise, ExercisePickerAdapter.VH>(DIFF) {
    inner class VH(val binding: ItemExerciseMatchBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener { onClick(getItem(bindingAdapterPosition)) }
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
        val ex = getItem(position)
        holder.binding.tvMatchName.text = ex.name
        holder.binding.tvMatchEquipment.text = ex.equipment
        holder.binding.cardMatch.strokeColor =
            holder.itemView.context.getColor(com.gymvoice.R.color.surface1)

        holder.binding.ivThumb.loadExerciseImage(ex.imageName)
    }

    companion object {
        val DIFF =
            object : DiffUtil.ItemCallback<Exercise>() {
                override fun areItemsTheSame(
                    a: Exercise,
                    b: Exercise,
                ) = a.id == b.id

                override fun areContentsTheSame(
                    a: Exercise,
                    b: Exercise,
                ) = a == b
            }
    }
}
