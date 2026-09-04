package com.samsunghealthexport.app

import com.samsunghealthexport.app.model.WorkoutDataPoint
import com.samsunghealthexport.app.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MetricCalculationsTest {

    @Test
    fun testPaceCalculationFromSpeedMps() {
        // 3.0 m/s = 10.8 km/h -> pace = 1000 / (3.0 * 60) = 5.555 min/km = 5 min 33 sec
        val pace = WorkoutDataPoint.calculatePaceMinPerKm(3.0)
        assertNotNull(pace)
        assertEquals(5.555, pace!!, 0.01)

        val formatted = WorkoutDataPoint.formatPace(pace)
        assertEquals("5'33\"/km", formatted)
    }

    @Test
    fun testPaceCalculationZeroSpeedReturnsNull() {
        assertNull(WorkoutDataPoint.calculatePaceMinPerKm(0.0))
        assertNull(WorkoutDataPoint.calculatePaceMinPerKm(0.05))
        assertEquals("--", WorkoutDataPoint.formatPace(null))
    }

    @Test
    fun testFormatDuration() {
        assertEquals("00:45", Formatters.formatDuration(45))
        assertEquals("12:30", Formatters.formatDuration(750))
        assertEquals("1:05:20", Formatters.formatDuration(3920))
    }

    @Test
    fun testFormatDistance() {
        assertEquals("5.25 km", Formatters.formatDistance(5250.0))
        assertEquals("0.80 km", Formatters.formatDistance(800.0))
    }
}
