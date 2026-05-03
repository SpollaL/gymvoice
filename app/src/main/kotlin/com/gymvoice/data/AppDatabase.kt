package com.gymvoice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [WorkoutLog::class, Exercise::class, UserCorrection::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutLogDao(): WorkoutLogDao

    abstract fun userCorrectionDao(): UserCorrectionDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val migration1To2 =
            object : Migration(1, 2) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS `user_corrections` " +
                            "(`sttFragment` TEXT NOT NULL, `correctedExercise` TEXT NOT NULL, " +
                            "PRIMARY KEY(`sttFragment`))",
                    )
                }
            }

        private val migration2To3 =
            object : Migration(2, 3) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE `logs` ADD COLUMN `restSeconds` INTEGER")
                }
            }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "gymvoice.db")
                    .addMigrations(migration1To2, migration2To3)
                    .build().also { instance = it }
            }
    }
}
