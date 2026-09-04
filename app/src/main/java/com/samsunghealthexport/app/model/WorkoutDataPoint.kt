package com.samsunghealthexport.app.model

import java.time.Instant

/**
 * Represents a single granular sample logged during the workout (Live Data point).
 * Contains synchronized GPS, speed, pace, heart rate, cadence, calories, and power.
 */
data class WorkoutDataPoint(
    val timestamp: Instant,
    val elapsedSeconds: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val accuracyMeters: Float? = null,
    val speedMps: Double? = null,
    val speedKmh: Double? = null,
    val paceMinPerKm: Double? = null,
    val paceFormatted: String? = null,
    val heartRateBpm: Int? = null,
    val distanceMeters: Double? = null,
    val cumulativeDistanceMeters: Double? = null,
    val cadenceSpm: Int? = null,
    val caloriesKcal: Double? = null,
    val powerWatts: Double? = null
) {
    companion object {
        /**
         * Calculates instantaneous pace in minutes per km from speed in meters per second.
         * Returns null if stopped or speed is negligible.
         */
        fun calculatePaceMinPerKm(speedMps: Double?): Double? {
            if (speedMps == null || speedMps <= 0.1) return null
            // pace (min/km) = 1000m / (speed * 60s)
            val pace = 1000.0 / (speedMps * 60.0)
            return if (pace in 1.5..35.0) pace else null // filter unrealistic paces
        }

        /**
         * Formats pace as mm:ss/km
         */
        fun formatPace(paceMinPerKm: Double?): String {
            if (paceMinPerKm == null || paceMinPerKm <= 0 || paceMinPerKm > 40) return "--"
            val totalSeconds = (paceMinPerKm * 60).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d'%02d\"/km".format(minutes, seconds)
        }
    }
}
