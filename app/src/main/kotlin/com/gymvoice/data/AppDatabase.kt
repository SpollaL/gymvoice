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
    version = 6,
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

        private val migration4To5 =
            object : Migration(4, 5) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("DELETE FROM `exercises`")
                }
            }

        private val migration5To6 =
            object : Migration(5, 6) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    val sql =
                        "INSERT OR IGNORE INTO exercises " +
                            "(name, muscleGroup, equipment, level, imageName) VALUES (?, ?, ?, ?, '')"
                    arrayOf(
                        arrayOf("Leverage Leg Press", "Legs", "machine", "beginner"),
                        arrayOf("Leverage Leg Press Unilateral", "Legs", "machine", "intermediate"),
                        arrayOf("Leverage Squat", "Legs", "machine", "intermediate"),
                        arrayOf("Leverage Seated Row", "Back", "machine", "beginner"),
                        arrayOf("Dumbbell Front Raise", "Shoulders", "dumbbell", "beginner"),
                    ).forEach { database.execSQL(sql, it) }
                }
            }

        private fun seedCallback(context: Context) =
            object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    collapseExpandedSetRows(db)
                    val cursor = db.query("SELECT COUNT(*) FROM exercises")
                    cursor.moveToFirst()
                    val count = cursor.getInt(0)
                    cursor.close()
                    if (count > 0) return
                    seedExercises(context, db)
                }
            }

        private fun collapseExpandedSetRows(db: SupportSQLiteDatabase) {
            // Removes rows added by the short-lived expansion migration.
            // Those rows share sessionId/exerciseName/reps/weight/timestamp with another row
            // but have a lower setNumber — only the max-setNumber row (the original) is kept.
            db.execSQL(
                "DELETE FROM logs WHERE id IN (" +
                    "SELECT l1.id FROM logs l1 " +
                    "INNER JOIN logs l2 ON l1.sessionId = l2.sessionId " +
                    "AND l1.exerciseName = l2.exerciseName " +
                    "AND COALESCE(l1.reps, -1) = COALESCE(l2.reps, -1) " +
                    "AND COALESCE(l1.weight, -1.0) = COALESCE(l2.weight, -1.0) " +
                    "AND l1.timestamp = l2.timestamp " +
                    "AND l1.id != l2.id " +
                    "WHERE l1.setNumber < l2.setNumber)",
            )
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
                    .addMigrations(migration1To2, migration2To3, migration3To4, migration4To5, migration5To6)
                    .addCallback(seedCallback(context.applicationContext))
                    .build().also { instance = it }
            }
    }
}
