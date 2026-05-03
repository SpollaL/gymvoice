package com.gymvoice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String = "",
)

@Entity(tableName = "logs")
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val exerciseName: String,
    val setNumber: Int?,
    val reps: Int?,
    val weight: Float?,
    val unit: String,
    val restSeconds: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
