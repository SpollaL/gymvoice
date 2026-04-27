package com.gymvoice.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.gymvoice.R
import com.gymvoice.data.WorkoutLog
import com.gymvoice.databinding.DialogEditLogBinding
import com.gymvoice.databinding.FragmentCalendarBinding
import com.gymvoice.databinding.ItemCalendarDayBinding
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarFragment : Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    val binding get() = _binding!!
    private val viewModel: CalendarViewModel by viewModels()
    private lateinit var adapter: WorkoutLogAdapter

    private var selectedDate: LocalDate = LocalDate.now()
    private var activeDates: Set<LocalDate> = emptySet()

    private val monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        setupCalendar()
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupCalendar() {
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(MONTHS_BACK)
        val endMonth = currentMonth.plusMonths(MONTHS_FORWARD)

        binding.calendarView.setup(startMonth, endMonth, DayOfWeek.MONDAY)
        binding.calendarView.scrollToMonth(currentMonth)
        binding.tvMonthYear.text = currentMonth.format(monthFormatter).uppercase(Locale.ENGLISH)

        binding.calendarView.monthScrollListener = { month ->
            binding.tvMonthYear.text = month.yearMonth.format(monthFormatter).uppercase(Locale.ENGLISH)
            viewModel.loadMonth(month.yearMonth)
        }

        binding.calendarView.dayBinder =
            object : MonthDayBinder<DayViewContainer> {
                override fun create(view: View) =
                    DayViewContainer(view) { day ->
                        if (day.position == DayPosition.MonthDate) viewModel.selectDate(day.date)
                    }

                override fun bind(
                    container: DayViewContainer,
                    data: CalendarDay,
                ) {
                    container.bind(data, selectedDate, activeDates, requireContext())
                }
            }

        binding.btnPrevMonth.setOnClickListener {
            val current = binding.calendarView.findFirstVisibleMonth()?.yearMonth ?: YearMonth.now()
            binding.calendarView.smoothScrollToMonth(current.minusMonths(1))
        }
        binding.btnNextMonth.setOnClickListener {
            val current = binding.calendarView.findFirstVisibleMonth()?.yearMonth ?: YearMonth.now()
            binding.calendarView.smoothScrollToMonth(current.plusMonths(1))
        }

        viewModel.loadMonth(currentMonth)
    }

    private fun setupRecyclerView() {
        adapter = WorkoutLogAdapter(onEdit = ::showEditDialog)
        binding.recyclerViewDay.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewDay.adapter = adapter
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.selectedDate.collect { date ->
                    selectedDate = date
                    binding.tvSelectedDate.text = date.format(dateFormatter).uppercase(Locale.ENGLISH)
                    binding.calendarView.notifyCalendarChanged()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.activeDates.collect { dates ->
                    activeDates = dates
                    binding.calendarView.notifyCalendarChanged()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.logsForDate.collect { logs -> adapter.submitList(logs) }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showEditDialog(log: WorkoutLog) {
        val dialogBinding = DialogEditLogBinding.inflate(layoutInflater)
        dialogBinding.etExercise.setText(log.exerciseName)
        dialogBinding.etSet.setText(log.setNumber?.toString() ?: "")
        dialogBinding.etReps.setText(log.reps?.toString() ?: "")
        dialogBinding.etWeight.setText(log.weight?.toString() ?: "")

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Log")
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val newExercise = dialogBinding.etExercise.text.toString()
                if (newExercise != log.exerciseName) {
                    viewModel.saveCorrection(log.exerciseName, newExercise)
                }
                viewModel.updateLog(
                    log.copy(
                        exerciseName = newExercise,
                        setNumber = dialogBinding.etSet.text.toString().toIntOrNull(),
                        reps = dialogBinding.etReps.text.toString().toIntOrNull(),
                        weight = dialogBinding.etWeight.text.toString().toFloatOrNull(),
                    ),
                )
            }
            .setNegativeButton("Delete") { _, _ -> viewModel.deleteLog(log) }
            .show()
    }

    companion object {
        private const val MONTHS_BACK = 12L
        private const val MONTHS_FORWARD = 12L
    }
}

class DayViewContainer(view: View, private val onClick: (CalendarDay) -> Unit) : ViewContainer(view) {
    private val binding = ItemCalendarDayBinding.bind(view)
    lateinit var day: CalendarDay

    init {
        view.setOnClickListener { onClick(day) }
    }

    fun bind(
        data: CalendarDay,
        selectedDate: LocalDate,
        activeDates: Set<LocalDate>,
        context: Context,
    ) {
        day = data
        binding.tvDay.text = data.date.dayOfMonth.toString()

        val colorText = ContextCompat.getColor(context, R.color.text)
        val colorMauve = ContextCompat.getColor(context, R.color.mauve)
        val colorBase = ContextCompat.getColor(context, R.color.base)
        val colorSurface1 = ContextCompat.getColor(context, R.color.surface1)

        when {
            data.position != DayPosition.MonthDate -> {
                binding.tvDay.setTextColor(colorSurface1)
                binding.tvDay.background = null
                binding.dotIndicator.isVisible = false
            }
            data.date == selectedDate -> {
                binding.tvDay.setTextColor(colorBase)
                binding.tvDay.background = ContextCompat.getDrawable(context, R.drawable.circle_selected)
                binding.dotIndicator.isVisible = false
            }
            data.date == LocalDate.now() -> {
                binding.tvDay.setTextColor(colorMauve)
                binding.tvDay.background = null
                binding.dotIndicator.isVisible = data.date in activeDates
            }
            else -> {
                binding.tvDay.setTextColor(colorText)
                binding.tvDay.background = null
                binding.dotIndicator.isVisible = data.date in activeDates
            }
        }
    }
}
