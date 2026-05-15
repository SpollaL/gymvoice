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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@Suppress("OPT_IN_USAGE")
class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.getInstance(app).exerciseDao()

    private val _muscleGroupFilter = MutableStateFlow<String?>(null)
    val muscleGroupFilter: StateFlow<String?> = _muscleGroupFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val muscleGroups: Flow<List<String>> = dao.getMuscleGroups()

    val exercises: Flow<List<Exercise>> =
        combine(_muscleGroupFilter, _searchQuery.debounce(300)) { group, query ->
            Pair(group, query)
        }.flatMapLatest { (group, query) ->
            when {
                query.length >= 2 -> flow { emit(dao.search(query)) }
                group != null -> dao.getByMuscleGroup(group)
                else -> dao.getAll()
            }
        }

    fun setMuscleGroup(group: String?) {
        _muscleGroupFilter.value = group
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
