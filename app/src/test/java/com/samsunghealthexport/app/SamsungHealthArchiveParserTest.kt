package com.samsunghealthexport.app

import com.samsunghealthexport.app.archive.SamsungHealthArchiveParser
import com.samsunghealthexport.app.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SamsungHealthArchiveParserTest {

    @Test
    fun testParseExerciseCsvWithLiveAndLocationData() {
        val testUuid = "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"

        val exerciseCsv = """
            "com.samsung.health.exercise.day_time","datauuid","exercise_type","start_time","end_time","duration","distance","calorie","mean_heart_rate","max_heart_rate","min_heart_rate","mean_speed","max_speed","mean_cadence","altitude_gain","comment"
            "2026-09-04 06:00:00.000","$testUuid","1001","2026-09-04 06:00:00.000","2026-09-04 06:30:00.000","1800000","5000.0","350.0","148.0","172","102","2.78","4.10","164","25.0","Morning 5K"
        """.trimIndent()

        val liveDataJson = """
            [
              {"start_time": 1788501600000, "speed": 2.7, "cadence": 160, "distance": 2.7, "calorie": 0.2, "heart_rate": 135},
              {"start_time": 1788501601000, "speed": 2.8, "cadence": 164, "distance": 2.8, "calorie": 0.4, "heart_rate": 140}
            ]
        """.trimIndent()

        val locationDataJson = """
            [
              {"start_time": 1788501600000, "latitude": 37.7749, "longitude": -122.4194, "altitude": 10.5, "accuracy": 3.0},
              {"start_time": 1788501601000, "latitude": 37.7750, "longitude": -122.4193, "altitude": 10.8, "accuracy": 2.5}
            ]
        """.trimIndent()

        val liveMap = mapOf(testUuid to liveDataJson)
        val locationMap = mapOf(testUuid to locationDataJson)

        val sessions = SamsungHealthArchiveParser.parseExerciseCsv(exerciseCsv, liveMap, locationMap)

        assertEquals("Parsed 1 workout session", 1, sessions.size)
        val s = sessions[0]
        assertEquals("UUID matched", testUuid, s.id)
        assertEquals("Running type identified", ExerciseType.RUNNING, s.exerciseType)
        assertEquals("Title from comment", "Morning 5K", s.title)
        assertEquals("5000m total distance", 5000.0, s.summary.totalDistanceMeters, 0.1)
        assertEquals("1800 seconds duration", 1800L, s.summary.totalDurationSeconds)

        // Verify correlated live points
        assertEquals("2 live data points synchronized", 2, s.liveDataPoints.size)
        val p1 = s.liveDataPoints[0]
        assertEquals(37.7749, p1.latitude!!, 0.0001)
        assertEquals(-122.4194, p1.longitude!!, 0.0001)
        assertEquals(135, p1.heartRateBpm)
        assertEquals(2.7, p1.speedMps!!, 0.01)
        assertEquals(160, p1.cadenceSpm)

        val p2 = s.liveDataPoints[1]
        assertEquals(37.7750, p2.latitude!!, 0.0001)
        assertEquals(140, p2.heartRateBpm)
        assertEquals(2.8, p2.speedMps!!, 0.01)
    }
}
