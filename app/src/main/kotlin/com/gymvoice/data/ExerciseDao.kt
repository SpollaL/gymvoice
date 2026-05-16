package com.gymvoice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE muscleGroup = :group ORDER BY name ASC")
    fun getByMuscleGroup(group: String): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun search(query: String): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: Long): Exercise?

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAllList(): List<Exercise>

    @Query("SELECT DISTINCT muscleGroup FROM exercises WHERE muscleGroup != '' ORDER BY muscleGroup ASC")
    fun getMuscleGroups(): Flow<List<String>>

    @Query("SELECT DISTINCT equipment FROM exercises WHERE equipment != '' ORDER BY equipment ASC")
    fun getEquipmentList(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<Exercise>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: Exercise): Long

    @androidx.room.Update
    suspend fun update(exercise: Exercise)

    @Query("SELECT COUNT(*) FROM exercises WHERE LOWER(name) = LOWER(:name)")
    suspend fun countByName(name: String): Int
}
