package com.gymvoice.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymvoice.R
import com.gymvoice.databinding.ItemRenameLogBinding

data class RenameItem(val logName: String, val canonicalName: String? = null)

class RenameLogsAdapter(
    private val onPickClick: (position: Int, logName: String) -> Unit,
    private val onChanged: () -> Unit,
) : ListAdapter<RenameItem, RenameLogsAdapter.VH>(DIFF) {
    inner class VH(val binding: ItemRenameLogBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onPickClick(pos, getItem(pos).logName)
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): VH = VH(ItemRenameLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(
        holder: VH,
        position: Int,
    ) {
        val item = getItem(position)
        holder.binding.tvLogName.text = item.logName
        if (item.canonicalName != null) {
            holder.binding.tvCanonical.text = item.canonicalName
            holder.binding.tvCanonical.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.mauve))
        } else {
            holder.binding.tvCanonical.text = "tap to pick"
            holder.binding.tvCanonical.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.overlay0))
        }
    }

    fun setTarget(
        position: Int,
        canonicalName: String,
    ) {
        val list = currentList.toMutableList()
        list[position] = list[position].copy(canonicalName = canonicalName)
        submitList(list)
        onChanged()
    }

    fun renameCount(): Int = currentList.count { it.canonicalName != null && it.canonicalName != it.logName }

    fun pendingRenames(): Map<String, String> =
        currentList
            .filter { it.canonicalName != null && it.canonicalName != it.logName }
            .associate { it.logName to it.canonicalName!! }

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<RenameItem>() {
                override fun areItemsTheSame(
                    a: RenameItem,
                    b: RenameItem,
                ) = a.logName == b.logName

                override fun areContentsTheSame(
                    a: RenameItem,
                    b: RenameItem,
                ) = a == b
            }
    }
}
