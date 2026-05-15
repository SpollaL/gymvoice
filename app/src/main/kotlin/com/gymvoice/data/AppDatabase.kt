package com.gymvoice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

@Database(
    entities = [WorkoutLog::class, Exercise::class, UserCorrection::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutLogDao(): WorkoutLogDao

    abstract fun userCorrectionDao(): UserCorrectionDao

    abstract fun exerciseDao(): ExerciseDao

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

        private val migration3To4 =
            object : Migration(3, 4) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE `exercises` ADD COLUMN `equipment` TEXT NOT NULL DEFAULT ''")
                    database.execSQL("ALTER TABLE `exercises` ADD COLUMN `level` TEXT NOT NULL DEFAULT ''")
                    database.execSQL("ALTER TABLE `exercises` ADD COLUMN `imageName` TEXT NOT NULL DEFAULT ''")
                }
            }

        private fun seedCallback(context: Context) =
            object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    val cursor = db.query("SELECT COUNT(*) FROM exercises")
                    cursor.moveToFirst()
                    val count = cursor.getInt(0)
                    cursor.close()
                    if (count > 0) return
                    seedExercises(context, db)
                }
            }

        private fun seedExercises(
            context: Context,
            db: SupportSQLiteDatabase,
        ) {
            val json =
                runCatching {
                    context.assets.open("exercises_seed.json").bufferedReader().readText()
                }.getOrNull() ?: return

            val array = runCatching { JSONArray(json) }.getOrNull() ?: return

            db.beginTransaction()
            try {
                for (i in 0 until array.length()) {
                    val ex = array.getJSONObject(i)
                    val sql =
                        "INSERT OR IGNORE INTO exercises " +
                            "(name, muscleGroup, equipment, level, imageName) VALUES (?, ?, ?, ?, ?)"
                    db.execSQL(
                        sql,
                        arrayOf(
                            ex.optString("name"),
                            ex.optString("muscleGroup"),
                            ex.optString("equipment"),
                            ex.optString("level"),
                            ex.optString("imageName"),
                        ),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context, AppDatabase::class.java, "gymvoice.db")
                    .addMigrations(migration1To2, migration2To3, migration3To4)
                    .addCallback(seedCallback(context.applicationContext))
                    .build().also { instance = it }
            }
    }
}
