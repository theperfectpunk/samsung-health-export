package com.samsunghealthexport.app.model

/**
 * Encapsulates the overall summary and averages for a workout session.
 */
data class WorkoutSummary(
    val totalDistanceMeters: Double = 0.0,
    val totalDurationSeconds: Long = 0L,
    val activeDurationSeconds: Long? = null,
    val avgHeartRateBpm: Double? = null,
    val maxHeartRateBpm: Int? = null,
    val minHeartRateBpm: Int? = null,
    val avgSpeedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val avgPaceMinPerKm: Double? = null,
    val bestPaceMinPerKm: Double? = null,
    val totalCaloriesKcal: Double? = null,
    val elevationGainMeters: Double? = null,
    val elevationLossMeters: Double? = null,
    val totalSteps: Int? = null,
    val avgCadenceSpm: Int? = null,
    val avgPowerWatts: Double? = null
) {
    val totalDistanceKm: Double
        get() = totalDistanceMeters / 1000.0

    val formattedDuration: String
        get() {
            val hours = totalDurationSeconds / 3600
            val minutes = (totalDurationSeconds % 3600) / 60
            val seconds = totalDurationSeconds % 60
            return if (hours > 0) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }

    val formattedAvgPace: String
        get() = WorkoutDataPoint.formatPace(avgPaceMinPerKm)

    val formattedBestPace: String
        get() = WorkoutDataPoint.formatPace(bestPaceMinPerKm)
}
