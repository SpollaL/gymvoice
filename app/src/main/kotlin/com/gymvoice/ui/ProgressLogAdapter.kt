package com.gymvoice.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gymvoice.data.WorkoutLog
import com.gymvoice.databinding.ItemProgressLogBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class ProgressLogAdapter : ListAdapter<WorkoutLog, ProgressLogAdapter.ViewHolder>(DIFF) {
    var prValue: Float? = null
    var hasWeight: Boolean = true

    private val dateFmt = DateTimeFormatter.ofPattern("EEE dd MMM", Locale.ENGLISH)

    inner class ViewHolder(private val binding: ItemProgressLogBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(log: WorkoutLog) {
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(log.timestamp).atZone(zone).toLocalDate()
            binding.tvDate.text =
                when (date) {
                    LocalDate.now() -> "Today"
                    LocalDate.now().minusDays(1) -> "Yesterday"
                    else -> date.format(dateFmt)
                }

            binding.tvDetails.text =
                buildString {
                    log.weight?.let { append("$it ${log.unit}  ") }
                    log.reps?.let { append("$it reps  ") }
                    log.setNumber?.let { append("set $it") }
                }.trim()

            val value = if (hasWeight) log.weight else log.reps?.toFloat()
            val pr = prValue
            binding.tvPr.isVisible = pr != null && value != null && abs(value - pr) < 0.01f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemProgressLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF =
            object : DiffUtil.ItemCallback<WorkoutLog>() {
                override fun areItemsTheSame(a: WorkoutLog, b: WorkoutLog) = a.id == b.id
                override fun areContentsTheSame(a: WorkoutLog, b: WorkoutLog) = a == b
            }
    }
}
