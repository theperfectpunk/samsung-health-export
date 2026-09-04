package com.samsunghealthexport.app

import com.samsunghealthexport.app.export.CsvExporter
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.ExportFormat
import com.samsunghealthexport.app.model.GeoPoint
import com.samsunghealthexport.app.model.WorkoutDataPoint
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.model.WorkoutSource
import com.samsunghealthexport.app.model.WorkoutSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class CsvExporterTest {

    @Test
    fun testCombinedCsvExportContainsAveragesAndLiveTrackData() {
        val start = Instant.parse("2026-09-04T07:00:00Z")
        val end = Instant.parse("2026-09-04T07:30:00Z")

        val pt1 = WorkoutDataPoint(
            timestamp = start,
            elapsedSeconds = 0,
            latitude = 37.7749,
            longitude = -122.4194,
            altitudeMeters = 15.0,
            accuracyMeters = 3.0f,
            speedMps = 3.0,
            speedKmh = 10.8,
            paceMinPerKm = 5.55,
            paceFormatted = "5'33\"/km",
            heartRateBpm = 142,
            distanceMeters = 0.0,
            cumulativeDistanceMeters = 0.0,
            cadenceSpm = 160,
            caloriesKcal = 0.0
        )

        val pt2 = WorkoutDataPoint(
            timestamp = start.plusSeconds(1),
            elapsedSeconds = 1,
            latitude = 37.7750,
            longitude = -122.4193,
            altitudeMeters = 15.2,
            accuracyMeters = 2.8f,
            speedMps = 3.2,
            speedKmh = 11.52,
            paceMinPerKm = 5.20,
            paceFormatted = "5'12\"/km",
            heartRateBpm = 145,
            distanceMeters = 3.2,
            cumulativeDistanceMeters = 3.2,
            cadenceSpm = 162,
            caloriesKcal = 0.2
        )

        val session = WorkoutSession.buildWithCalculatedAverages(
            id = "test-uuid-1234",
            source = WorkoutSource.SAMSUNG_HEALTH_CONNECT,
            exerciseType = ExerciseType.RUNNING,
            title = "Morning Run",
            startTime = start,
            endTime = end,
            liveDataPoints = listOf(pt1, pt2)
        )

        val csv = CsvExporter.exportWorkout(session, ExportFormat.COMBINED_CSV)

        // Check metadata & averages header
        assertTrue("Contains title", csv.contains("Morning Run"))
        assertTrue("Contains Running sport", csv.contains("Running"))
        assertTrue("Contains Start Time", csv.contains("2026-09-04T07:00:00Z"))
        assertTrue("Contains Average Heart Rate header", csv.contains("Average Heart Rate (bpm)"))
        assertTrue("Contains Average Speed header", csv.contains("Average Speed (km/h)"))
        assertTrue("Contains Average Pace header", csv.contains("Average Pace (min/km)"))

        // Check column header
        assertTrue("Contains column headers", csv.contains("Timestamp,Elapsed_Seconds,Latitude,Longitude"))
        assertTrue("Contains Heart Rate column", csv.contains("Heart_Rate_bpm"))
        assertTrue("Contains Pace column", csv.contains("Pace_min_per_km"))

        // Check live rows
        assertTrue("Contains point 1 coords", csv.contains("37.7749000,-122.4194000"))
        assertTrue("Contains point 1 HR", csv.contains(",142,"))
        assertTrue("Contains point 2 coords", csv.contains("37.7750000,-122.4193000"))
        assertTrue("Contains point 2 HR", csv.contains(",145,"))
    }

    @Test
    fun testLiveStreamingCsvWriter() {
        val tempFile = File.createTempFile("live_test_", ".csv")
        try {
            val writer = CsvExporter.LiveCsvWriter(tempFile)
            val now = Instant.now()

            writer.writePoint(
                WorkoutDataPoint(
                    timestamp = now,
                    elapsedSeconds = 0,
                    latitude = 40.7128,
                    longitude = -74.0060,
                    speedMps = 2.8,
                    speedKmh = 10.08,
                    paceMinPerKm = 5.95,
                    heartRateBpm = 138,
                    distanceMeters = 0.0,
                    cumulativeDistanceMeters = 0.0
                )
            )

            writer.writePoint(
                WorkoutDataPoint(
                    timestamp = now.plusSeconds(1),
                    elapsedSeconds = 1,
                    latitude = 40.7129,
                    longitude = -74.0061,
                    speedMps = 3.0,
                    speedKmh = 10.8,
                    paceMinPerKm = 5.55,
                    heartRateBpm = 140,
                    distanceMeters = 2.8,
                    cumulativeDistanceMeters = 2.8
                )
            )
            writer.close()

            val lines = tempFile.readLines()
            assertEquals("Header + 2 points = 3 lines", 3, lines.size)
            assertTrue("Header contains Speed_kmh", lines[0].contains("Speed_kmh"))
            assertTrue("First row has HR 138", lines[1].contains(",138,"))
            assertTrue("Second row has HR 140", lines[2].contains(",140,"))
        } finally {
            tempFile.delete()
        }
    }
}
