package com.gymvoice.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutLogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutLogDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        dao = db.workoutLogDao()
    }

    @After
    fun teardown() = db.close()

    @Test
    fun getLogsInRange_returnsOnlyLogsWithinRange() = runTest {
        dao.insert(WorkoutLog(sessionId = "s1", exerciseName = "bench", setNumber = 1,
            reps = 10, weight = 80f, unit = "kg", timestamp = 1000L))
        dao.insert(WorkoutLog(sessionId = "s2", exerciseName = "squat", setNumber = 1,
            reps = 5, weight = 100f, unit = "kg", timestamp = 5000L))
        dao.insert(WorkoutLog(sessionId = "s3", exerciseName = "row", setNumber = 1,
            reps = 8, weight = 60f, unit = "kg", timestamp = 9000L))

        val result = dao.getLogsInRange(start = 1000L, end = 5000L)

        assertEquals(2, result.size)
        assertEquals("bench", result[0].exerciseName)
        assertEquals("squat", result[1].exerciseName)
    }

    @Test
    fun getLogsInRange_allTime_returnsAll() = runTest {
        dao.insert(WorkoutLog(sessionId = "s1", exerciseName = "bench", setNumber = 1,
            reps = 10, weight = 80f, unit = "kg", timestamp = 1000L))
        dao.insert(WorkoutLog(sessionId = "s2", exerciseName = "squat", setNumber = 1,
            reps = 5, weight = 100f, unit = "kg", timestamp = 5000L))

        val result = dao.getLogsInRange(start = 0L, end = Long.MAX_VALUE)

        assertEquals(2, result.size)
    }

    @Test
    fun getLogsInRange_emptyRange_returnsEmpty() = runTest {
        dao.insert(WorkoutLog(sessionId = "s1", exerciseName = "bench", setNumber = 1,
            reps = 10, weight = 80f, unit = "kg", timestamp = 1000L))

        val result = dao.getLogsInRange(start = 2000L, end = 3000L)

        assertEquals(0, result.size)
    }
}
