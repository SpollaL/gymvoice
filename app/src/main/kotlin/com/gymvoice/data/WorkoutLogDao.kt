package com.gymvoice.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Insert
    suspend fun insert(log: WorkoutLog): Long

    @Update
    suspend fun update(log: WorkoutLog)

    @Delete
    suspend fun delete(log: WorkoutLog)
}
