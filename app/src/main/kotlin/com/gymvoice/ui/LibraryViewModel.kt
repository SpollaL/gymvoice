package com.gymvoice.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.gymvoice.data.AppDatabase
import com.gymvoice.data.Exercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce

@Suppress("OPT_IN_USAGE")
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).exerciseDao()

    private val _muscleGroupFilter = MutableStateFlow<String?>(null)
    val muscleGroupFilter: StateFlow<String?> = _muscleGroupFilter.asStateFlow()

    private val _equipmentFilter = MutableStateFlow<String?>(null)
    val equipmentFilter: StateFlow<String?> = _equipmentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val muscleGroups: Flow<List<String>> = dao.getMuscleGroups()
    val equipmentList: Flow<List<String>> = dao.getEquipmentList()

    val exercises: Flow<List<Exercise>> =
        combine(
            dao.getAll(),
            _muscleGroupFilter,
            _equipmentFilter,
            _searchQuery.debounce(300),
        ) { all, muscle, equipment, query ->
            all.filter { ex ->
                (muscle == null || ex.muscleGroup == muscle) &&
                    (equipment == null || ex.equipment == equipment) &&
                    (query.length < 2 || ex.name.contains(query, ignoreCase = true))
            }
        }

    fun setMuscleGroup(group: String?) {
        _muscleGroupFilter.value = group
    }

    fun setEquipmentFilter(equipment: String?) {
        _equipmentFilter.value = equipment
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
