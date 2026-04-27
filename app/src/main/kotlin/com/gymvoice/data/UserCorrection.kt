package com.gymvoice.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "user_corrections")
data class UserCorrection(
    @PrimaryKey val sttFragment: String,
    val correctedExercise: String,
)

@Dao
interface UserCorrectionDao {
    @Query("SELECT * FROM user_corrections")
    suspend fun getAll(): List<UserCorrection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(correction: UserCorrection)
}
