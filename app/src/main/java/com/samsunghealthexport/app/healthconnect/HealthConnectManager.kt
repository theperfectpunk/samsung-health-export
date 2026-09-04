package com.samsunghealthexport.app.healthconnect

import android.content.Context
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

    private val healthConnectClient by lazy {
        if (isAvailable()) HealthConnectClient.getOrCreate(context) else null
    }

    fun isAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        // Check if required permissions are subset
        return granted.containsAll(HealthConnectPermissions.PERMISSIONS.filter { !it.contains("ROUTES") })
    }

    /**
     * Reads all exercise sessions recorded within the specified time range.
     * Extracts full GPS routes, heart rate samples, speed samples, steps, and energy.
     */
    suspend fun fetchWorkouts(
        startTime: Instant = Instant.now().minus(Duration.ofDays(30)),
        endTime: Instant = Instant.now()
    ): List<WorkoutSession> = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext emptyList()

        try {
            val sessionsResponse = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )

            sessionsResponse.records.map { sessionRecord ->
                fetchDetailedWorkout(client, sessionRecord)
            }.sortedByDescending { it.startTime }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun fetchDetailedWorkout(
        client: HealthConnectClient,
        session: ExerciseSessionRecord
    ): WorkoutSession {
        val timeFilter = TimeRangeFilter.between(session.startTime, session.endTime)

        // Read Heart Rate series
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

        // Read Speed series
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

        // Read Distance records
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

        // Read Steps records
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

        // Read Calories records
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

        // Read Elevation Gained
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

        // Read Power records
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

        // Extract GPS Route locations
        val routeLocations = mutableListOf<ExerciseRoute.Location>()
        val routeResult = session.exerciseRouteResult
        if (routeResult is ExerciseRouteResult.Data) {
            routeLocations.addAll(routeResult.exerciseRoute.route)
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

            // Compute distance delta if GPS is present
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
        val title = session.title ?: "${exerciseType.displayName} Session"

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
        return 6371000.0 * c // Earth radius in meters
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
