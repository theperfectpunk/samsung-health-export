package com.samsunghealthexport.app.model

import java.time.Instant

enum class WorkoutSource(val label: String) {
    SAMSUNG_HEALTH_CONNECT("Samsung Health (Health Connect)"),
    SAMSUNG_HEALTH_ARCHIVE("Samsung Health Data Export"),
    LIVE_TRACKER("Live Workout Tracker")
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val timestamp: Instant? = null
)

/**
 * Complete representation of a workout session, holding metadata,
 * computed/reported averages, full live data points, and GPS route.
 */
data class WorkoutSession(
    val id: String,
    val source: WorkoutSource,
    val exerciseType: ExerciseType,
    val title: String,
    val startTime: Instant,
    val endTime: Instant,
    val summary: WorkoutSummary,
    val liveDataPoints: List<WorkoutDataPoint> = emptyList(),
    val routePoints: List<GeoPoint> = emptyList(),
    val notes: String? = null
) {
    val durationSeconds: Long
        get() = summary.totalDurationSeconds.takeIf { it > 0 }
            ?: (endTime.epochSecond - startTime.epochSecond).coerceAtLeast(0)

    val hasGpsRoute: Boolean
        get() = routePoints.isNotEmpty() || liveDataPoints.any { it.latitude != null }

    val hasHeartRate: Boolean
        get() = summary.avgHeartRateBpm != null || liveDataPoints.any { it.heartRateBpm != null }

    val hasSpeed: Boolean
        get() = summary.avgSpeedKmh != null || liveDataPoints.any { it.speedKmh != null }

    companion object {
        /**
         * Re-aggregates or backfills missing averages from the granular live data points.
         */
        fun buildWithCalculatedAverages(
            id: String,
            source: WorkoutSource,
            exerciseType: ExerciseType,
            title: String,
            startTime: Instant,
            endTime: Instant,
            existingSummary: WorkoutSummary? = null,
            liveDataPoints: List<WorkoutDataPoint>,
            routePoints: List<GeoPoint> = emptyList(),
            notes: String? = null
        ): WorkoutSession {
            val durationSeconds = existingSummary?.totalDurationSeconds?.takeIf { it > 0 }
                ?: (endTime.epochSecond - startTime.epochSecond).coerceAtLeast(0)

            // Heart Rate stats
            val hrPoints = liveDataPoints.mapNotNull { it.heartRateBpm }
            val avgHr = existingSummary?.avgHeartRateBpm ?: if (hrPoints.isNotEmpty()) hrPoints.average() else null
            val maxHr = existingSummary?.maxHeartRateBpm ?: hrPoints.maxOrNull()
            val minHr = existingSummary?.minHeartRateBpm ?: hrPoints.minOrNull()

            // Speed stats
            val speedPoints = liveDataPoints.mapNotNull { it.speedKmh }.filter { it > 0.5 }
            val avgSpeedKmh = existingSummary?.avgSpeedKmh ?: if (speedPoints.isNotEmpty()) speedPoints.average() else null
            val maxSpeedKmh = existingSummary?.maxSpeedKmh ?: speedPoints.maxOrNull()

            // Distance stats
            val lastPointDist = liveDataPoints.lastOrNull()?.cumulativeDistanceMeters
            val sumOfIntervalDistances = liveDataPoints.mapNotNull { it.distanceMeters }.sum()
            val totalDistanceM = existingSummary?.totalDistanceMeters?.takeIf { it > 0 }
                ?: lastPointDist?.takeIf { it > 0 }
                ?: sumOfIntervalDistances

            // Pace stats
            val paces = liveDataPoints.mapNotNull { it.paceMinPerKm }.filter { it in 2.0..30.0 }
            val avgPace = existingSummary?.avgPaceMinPerKm ?: run {
                if (totalDistanceM > 50 && durationSeconds > 10) {
                    val distanceKm = totalDistanceM / 1000.0
                    (durationSeconds / 60.0) / distanceKm
                } else if (paces.isNotEmpty()) {
                    paces.average()
                } else null
            }
            val bestPace = existingSummary?.bestPaceMinPerKm ?: paces.minOrNull()

            // Cadence
            val cadences = liveDataPoints.mapNotNull { it.cadenceSpm }.filter { it > 0 }
            val avgCadence = existingSummary?.avgCadenceSpm ?: if (cadences.isNotEmpty()) cadences.average().toInt() else null

            // Calories
            val totalCalories = existingSummary?.totalCaloriesKcal
                ?: liveDataPoints.mapNotNull { it.caloriesKcal }.maxOrNull()
                ?: liveDataPoints.mapNotNull { it.caloriesKcal }.sum().takeIf { it > 0 }

            val mergedSummary = WorkoutSummary(
                totalDistanceMeters = totalDistanceM,
                totalDurationSeconds = durationSeconds,
                activeDurationSeconds = existingSummary?.activeDurationSeconds ?: durationSeconds,
                avgHeartRateBpm = avgHr,
                maxHeartRateBpm = maxHr,
                minHeartRateBpm = minHr,
                avgSpeedKmh = avgSpeedKmh,
                maxSpeedKmh = maxSpeedKmh,
                avgPaceMinPerKm = avgPace,
                bestPaceMinPerKm = bestPace,
                totalCaloriesKcal = totalCalories,
                elevationGainMeters = existingSummary?.elevationGainMeters,
                elevationLossMeters = existingSummary?.elevationLossMeters,
                totalSteps = existingSummary?.totalSteps,
                avgCadenceSpm = avgCadence,
                avgPowerWatts = existingSummary?.avgPowerWatts
            )

            return WorkoutSession(
                id = id,
                source = source,
                exerciseType = exerciseType,
                title = title,
                startTime = startTime,
                endTime = endTime,
                summary = mergedSummary,
                liveDataPoints = liveDataPoints,
                routePoints = routePoints,
                notes = notes
            )
        }
    }
}
