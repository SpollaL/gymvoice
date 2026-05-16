package com.gymvoice.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
@Dao
interface WorkoutLogDao {
    @Query("SELECT * FROM logs WHERE timestamp > :startOfDay ORDER BY timestamp DESC")
    fun getTodayLogs(startOfDay: Long): Flow<List<WorkoutLog>>

    @Query("SELECT * FROM logs WHERE timestamp >= :startOfDay AND timestamp < :endOfDay ORDER BY timestamp DESC")
    fun getLogsForDate(
        startOfDay: Long,
        endOfDay: Long,
    ): Flow<List<WorkoutLog>>

    @Query("SELECT timestamp FROM logs WHERE timestamp >= :start AND timestamp < :end")
    suspend fun getTimestampsInRange(
        start: Long,
        end: Long,
    ): List<Long>

    @Query("SELECT DISTINCT exerciseName FROM logs ORDER BY exerciseName ASC")
    fun getDistinctExercises(): Flow<List<String>>

    @Query("SELECT * FROM logs WHERE exerciseName = :exercise ORDER BY timestamp ASC")
    fun getLogsForExercise(exercise: String): Flow<List<WorkoutLog>>

    @Query("SELECT * FROM logs WHERE timestamp >= :start AND timestamp <= :end ORDER BY timestamp ASC")
    suspend fun getLogsInRange(
        start: Long,
        end: Long,
    ): List<WorkoutLog>

    @Query("SELECT timestamp FROM logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestTimestamp(): Long?

    @Query("SELECT DISTINCT exerciseName FROM logs ORDER BY exerciseName ASC")
    suspend fun getDistinctExerciseNamesOnce(): List<String>

    @Query("SELECT exerciseName FROM logs GROUP BY exerciseName ORDER BY MAX(timestamp) DESC LIMIT :limit")
    suspend fun getRecentExerciseNames(limit: Int): List<String>

    @Query("UPDATE logs SET exerciseName = :newName WHERE exerciseName = :oldName")
    suspend fun renameExercise(
        oldName: String,
        newName: String,
    ): Int

    @Query("SELECT * FROM logs WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getLogsAfter(since: Long): Flow<List<WorkoutLog>>

    @Insert
    suspend fun insert(log: WorkoutLog): Long

    @Update
    suspend fun update(log: WorkoutLog)

    @Delete
    suspend fun delete(log: WorkoutLog)
}
