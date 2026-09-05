package com.samsunghealthexport.app.healthconnect

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseRoute
import androidx.health.connect.client.records.ExerciseRouteResult
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.GeoPoint
import com.samsunghealthexport.app.model.WorkoutDataPoint
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.model.WorkoutSource
import com.samsunghealthexport.app.model.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.util.TreeMap

class HealthConnectManager(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnectManager"
    }

    private val healthConnectClient by lazy {
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null
    }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun getGrantedPermissions(): Set<String> {
        val client = healthConnectClient ?: return emptySet()
        return try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking granted permissions", e)
            emptySet()
        }
    }

    /**
     * Checks if the minimum required permission to read exercise sessions is granted.
     */
    suspend fun hasRequiredPermissions(): Boolean {
        val granted = getGrantedPermissions()
        return granted.contains(HealthConnectPermissions.REQUIRED_EXERCISE_PERMISSION)
    }

    /**
     * Checks if historical data access permission (reading older than 30 days) is granted.
     */
    suspend fun hasHistoryPermission(): Boolean {
        val granted = getGrantedPermissions()
        return granted.contains(HealthConnectPermissions.READ_HEALTH_DATA_HISTORY)
    }

    /**
     * Reads all exercise sessions recorded within the specified time range.
     * Uses pagination to retrieve all records and handles historical limits safely.
     */
    suspend fun fetchWorkouts(
        requestedStartTime: Instant = Instant.now().minus(Duration.ofDays(365)),
        endTime: Instant = Instant.now()
    ): List<WorkoutSession> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: run {
            Log.w(TAG, "HealthConnectClient is null")
            return@withContext emptyList()
        }

        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Missing required READ_EXERCISE permission")
            return@withContext emptyList()
        }

        // Safety check: if historical permission is not granted, clamp to 29 days to prevent SecurityException
        val hasHistory = hasHistoryPermission()
        val earliestAllowed = Instant.now().minus(Duration.ofDays(29))
        val effectiveStartTime = if (!hasHistory && requestedStartTime.isBefore(earliestAllowed)) {
            Log.i(TAG, "Clamping query to 29 days because READ_HEALTH_DATA_HISTORY is not granted")
            earliestAllowed
        } else {
            requestedStartTime
        }

        val allSessionRecords = mutableListOf<ExerciseSessionRecord>()
        var pageToken: String? = null

        try {
            do {
                val request = ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(effectiveStartTime, endTime),
                    pageToken = pageToken,
                    pageSize = 500
                )
                val response = client.readRecords(request)
                allSessionRecords.addAll(response.records)
                pageToken = response.pageToken
            } while (pageToken != null)

            Log.i(TAG, "Found ${allSessionRecords.size} ExerciseSessionRecords in Health Connect")
        } catch (e: Exception) {
            Log.e(TAG, "Error querying ExerciseSessionRecord with start=$effectiveStartTime", e)
            // If it failed due to date range, attempt fallback to last 28 days
            if (effectiveStartTime.isBefore(earliestAllowed)) {
                try {
                    val fallbackRequest = ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = TimeRangeFilter.between(earliestAllowed, endTime),
                        pageSize = 500
                    )
                    val fallbackResponse = client.readRecords(fallbackRequest)
                    allSessionRecords.addAll(fallbackResponse.records)
                    Log.i(TAG, "Fallback query retrieved ${allSessionRecords.size} records")
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Fallback query also failed", fallbackEx)
                }
            }
        }

        // Fetch details for each session with per-record fault tolerance
        allSessionRecords.mapNotNull { sessionRecord ->
            try {
                fetchDetailedWorkout(client, sessionRecord)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching details for session ${sessionRecord.metadata.id}, creating basic session", e)
                createBasicWorkoutSession(sessionRecord)
            }
        }.sortedByDescending { it.startTime }
    }

    private suspend fun fetchDetailedWorkout(
        client: HealthConnectClient,
        session: ExerciseSessionRecord
    ): WorkoutSession {
        val timeFilter = TimeRangeFilter.between(session.startTime, session.endTime)

        // Read Heart Rate series safely
        val hrRecords = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = timeFilter
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        // Read Speed series safely
        val speedRecords = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SpeedRecord::class,
                    timeRangeFilter = timeFilter
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        // Read Distance records safely
        val distanceRecords = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = timeFilter
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        // Read Steps records safely
        val stepsRecords = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = timeFilter
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        // Read Calories records safely
        val caloriesRecords = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = timeFilter
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        // Read Elevation Gained safely
        val elevationRecords = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = ElevationGainedRecord::class,
                    timeRangeFilter = timeFilter
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        // Read Power records safely
        val powerRecords = try {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = PowerRecord::class,
                    timeRangeFilter = timeFilter
                )
            ).records
        } catch (e: Exception) {
            emptyList()
        }

        // Extract GPS Route locations safely
        val routeLocations = mutableListOf<ExerciseRoute.Location>()
        try {
            val routeResult = session.exerciseRouteResult
            if (routeResult is ExerciseRouteResult.Data) {
                routeLocations.addAll(routeResult.exerciseRoute.route)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read exercise route result for session ${session.metadata.id}: ${e.message}")
        }

        val geoPoints = routeLocations.map { loc ->
            GeoPoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitudeMeters = loc.altitude?.inMeters,
                timestamp = loc.time
            )
        }

        // Aggregate Totals
        val totalDistanceMeters = distanceRecords.sumOf { it.distance.inMeters }
        val totalSteps = stepsRecords.sumOf { it.count }
        val totalCaloriesKcal = caloriesRecords.sumOf { it.energy.inKilocalories }
        val totalElevationGain = elevationRecords.sumOf { it.elevation.inMeters }

        // Merge granular points by timestamp into synchronized second-by-second timeline
        val timelineMap = TreeMap<Instant, MutableDataPointBuilder>()

        // 1. Add GPS route points
        routeLocations.forEach { loc ->
            val builder = timelineMap.getOrPut(loc.time) { MutableDataPointBuilder(loc.time) }
            builder.latitude = loc.latitude
            builder.longitude = loc.longitude
            builder.altitudeMeters = loc.altitude?.inMeters
            builder.accuracyMeters = loc.horizontalAccuracy?.inMeters?.toFloat()
        }

        // 2. Add Heart Rate samples
        hrRecords.forEach { record ->
            record.samples.forEach { sample ->
                val builder = timelineMap.getOrPut(sample.time) { MutableDataPointBuilder(sample.time) }
                builder.heartRateBpm = sample.beatsPerMinute.toInt()
            }
        }

        // 3. Add Speed samples
        speedRecords.forEach { record ->
            record.samples.forEach { sample ->
                val builder = timelineMap.getOrPut(sample.time) { MutableDataPointBuilder(sample.time) }
                val mps = sample.speed.inMetersPerSecond
                builder.speedMps = mps
                builder.speedKmh = mps * 3.6
                builder.paceMinPerKm = WorkoutDataPoint.calculatePaceMinPerKm(mps)
            }
        }

        // 4. Add Power samples
        powerRecords.forEach { record ->
            record.samples.forEach { sample ->
                val builder = timelineMap.getOrPut(sample.time) { MutableDataPointBuilder(sample.time) }
                builder.powerWatts = sample.power.inWatts
            }
        }

        // Convert sorted map to List<WorkoutDataPoint> with cumulative distance calculation
        var runningDistance = 0.0
        var lastLocation: GeoPoint? = null

        val liveDataPoints = timelineMap.values.map { b ->
            val elapsed = (b.time.epochSecond - session.startTime.epochSecond).coerceAtLeast(0)

            if (b.latitude != null && b.longitude != null) {
                val currentPoint = GeoPoint(b.latitude!!, b.longitude!!)
                if (lastLocation != null) {
                    val delta = calculateDistanceMeters(lastLocation!!, currentPoint)
                    runningDistance += delta
                    b.intervalDistanceM = delta
                }
                lastLocation = currentPoint
            }
            b.cumulativeDistanceM = runningDistance

            b.toDataPoint(elapsed)
        }

        val exerciseType = ExerciseType.fromHealthConnectType(session.exerciseType)
        val title = session.title?.takeIf { it.isNotBlank() } ?: "${exerciseType.displayName} Session"

        val summary = WorkoutSummary(
            totalDistanceMeters = if (totalDistanceMeters > 0) totalDistanceMeters else runningDistance,
            totalDurationSeconds = (session.endTime.epochSecond - session.startTime.epochSecond).coerceAtLeast(0),
            totalCaloriesKcal = if (totalCaloriesKcal > 0) totalCaloriesKcal else null,
            elevationGainMeters = if (totalElevationGain > 0) totalElevationGain else null,
            totalSteps = if (totalSteps > 0) totalSteps.toInt() else null
        )

        return WorkoutSession.buildWithCalculatedAverages(
            id = session.metadata.id,
            source = WorkoutSource.SAMSUNG_HEALTH_CONNECT,
            exerciseType = exerciseType,
            title = title,
            startTime = session.startTime,
            endTime = session.endTime,
            existingSummary = summary,
            liveDataPoints = liveDataPoints,
            routePoints = geoPoints,
            notes = session.notes
        )
    }

    private fun createBasicWorkoutSession(session: ExerciseSessionRecord): WorkoutSession {
        val exerciseType = ExerciseType.fromHealthConnectType(session.exerciseType)
        val duration = (session.endTime.epochSecond - session.startTime.epochSecond).coerceAtLeast(0)
        return WorkoutSession(
            id = session.metadata.id,
            source = WorkoutSource.SAMSUNG_HEALTH_CONNECT,
            exerciseType = exerciseType,
            title = session.title?.takeIf { it.isNotBlank() } ?: "${exerciseType.displayName} Session",
            startTime = session.startTime,
            endTime = session.endTime,
            summary = WorkoutSummary(totalDurationSeconds = duration),
            liveDataPoints = emptyList(),
            routePoints = emptyList(),
            notes = session.notes
        )
    }

    private fun calculateDistanceMeters(p1: GeoPoint, p2: GeoPoint): Double {
        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371000.0 * c
    }

    private class MutableDataPointBuilder(val time: Instant) {
        var latitude: Double? = null
        var longitude: Double? = null
        var altitudeMeters: Double? = null
        var accuracyMeters: Float? = null
        var speedMps: Double? = null
        var speedKmh: Double? = null
        var paceMinPerKm: Double? = null
        var heartRateBpm: Int? = null
        var intervalDistanceM: Double? = null
        var cumulativeDistanceM: Double? = null
        var cadenceSpm: Int? = null
        var caloriesKcal: Double? = null
        var powerWatts: Double? = null

        fun toDataPoint(elapsedSeconds: Long): WorkoutDataPoint {
            return WorkoutDataPoint(
                timestamp = time,
                elapsedSeconds = elapsedSeconds,
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
                accuracyMeters = accuracyMeters,
                speedMps = speedMps,
                speedKmh = speedKmh,
                paceMinPerKm = paceMinPerKm,
                paceFormatted = WorkoutDataPoint.formatPace(paceMinPerKm),
                heartRateBpm = heartRateBpm,
                distanceMeters = intervalDistanceM,
                cumulativeDistanceMeters = cumulativeDistanceM,
                cadenceSpm = cadenceSpm,
                caloriesKcal = caloriesKcal,
                powerWatts = powerWatts
            )
        }
    }
}
