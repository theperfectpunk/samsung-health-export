package com.samsunghealthexport.app.live

import android.content.Context
import android.location.Location
import com.samsunghealthexport.app.export.CsvExporter
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.GeoPoint
import com.samsunghealthexport.app.model.WorkoutDataPoint
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.model.WorkoutSource
import com.samsunghealthexport.app.model.WorkoutSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.UUID

data class LiveWorkoutState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val exerciseType: ExerciseType = ExerciseType.RUNNING,
    val elapsedSeconds: Long = 0L,
    val totalDistanceMeters: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val currentPaceFormatted: String = "--",
    val currentHeartRateBpm: Int? = null,
    val currentAltitudeMeters: Double? = null,
    val avgSpeedKmh: Double? = null,
    val avgPaceFormatted: String = "--",
    val avgHeartRateBpm: Double? = null,
    val caloriesKcal: Double = 0.0,
    val pointsCount: Int = 0,
    val liveCsvPath: String? = null
)

class LiveWorkoutTracker private constructor() {

    companion object {
        val instance by lazy { LiveWorkoutTracker() }
    }

    private val scope = CoroutineScope(Dispatchers.Default)
    private var timerJob: Job? = null

    private val _state = MutableStateFlow(LiveWorkoutState())
    val state: StateFlow<LiveWorkoutState> = _state.asStateFlow()

    private var sessionStartTime: Instant? = null
    private var currentWorkoutId: String = ""
    private var liveCsvWriter: CsvExporter.LiveCsvWriter? = null
    private var liveCsvFile: File? = null

    private val recordedPoints = mutableListOf<WorkoutDataPoint>()
    private var lastLocation: Location? = null
    private var lastRecordedHeartRate: Int? = null

    fun startWorkout(context: Context, exerciseType: ExerciseType) {
        if (_state.value.isTracking) return

        currentWorkoutId = UUID.randomUUID().toString()
        sessionStartTime = Instant.now()
        recordedPoints.clear()
        lastLocation = null

        // Initialize Live CSV file for streaming during workout
        val exportsDir = File(context.filesDir, "live_exports")
        exportsDir.mkdirs()
        val csvFile = File(exportsDir, "live_workout_${System.currentTimeMillis()}.csv")
        liveCsvFile = csvFile
        liveCsvWriter = CsvExporter.LiveCsvWriter(csvFile)

        _state.value = LiveWorkoutState(
            isTracking = true,
            isPaused = false,
            exerciseType = exerciseType,
            elapsedSeconds = 0L,
            liveCsvPath = csvFile.absolutePath
        )

        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (!_state.value.isPaused && _state.value.isTracking) {
                    val newElapsed = _state.value.elapsedSeconds + 1
                    updateMetrics(newElapsed)
                }
            }
        }
    }

    fun pauseWorkout() {
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resumeWorkout() {
        _state.value = _state.value.copy(isPaused = false)
    }

    fun onHeartRateUpdate(bpm: Int) {
        lastRecordedHeartRate = bpm
        _state.value = _state.value.copy(currentHeartRateBpm = bpm)
    }

    fun onLocationUpdate(location: Location) {
        if (!_state.value.isTracking || _state.value.isPaused) return

        val prevLoc = lastLocation
        var deltaM = 0.0
        if (prevLoc != null) {
            deltaM = prevLoc.distanceTo(location).toDouble()
            // Filter GPS jitter/glitches
            if (deltaM < 0.3) deltaM = 0.0
        }
        lastLocation = location

        val totalDist = _state.value.totalDistanceMeters + deltaM
        val speedMps = if (location.hasSpeed() && location.speed > 0) location.speed.toDouble() else 0.0
        val speedKmh = speedMps * 3.6
        val pace = WorkoutDataPoint.calculatePaceMinPerKm(speedMps)

        val point = WorkoutDataPoint(
            timestamp = Instant.ofEpochMilli(location.time),
            elapsedSeconds = _state.value.elapsedSeconds,
            latitude = location.latitude,
            longitude = location.longitude,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            speedMps = speedMps,
            speedKmh = speedKmh,
            paceMinPerKm = pace,
            paceFormatted = WorkoutDataPoint.formatPace(pace),
            heartRateBpm = lastRecordedHeartRate,
            distanceMeters = deltaM,
            cumulativeDistanceMeters = totalDist,
            caloriesKcal = estimateCalories(_state.value.exerciseType, totalDist, _state.value.elapsedSeconds)
        )

        recordedPoints.add(point)

        // Write live to CSV file immediately!
        liveCsvWriter?.writePoint(point)

        _state.value = _state.value.copy(
            totalDistanceMeters = totalDist,
            currentSpeedKmh = speedKmh,
            currentPaceFormatted = WorkoutDataPoint.formatPace(pace),
            currentAltitudeMeters = if (location.hasAltitude()) location.altitude else null,
            pointsCount = recordedPoints.size
        )
    }

    private fun updateMetrics(newElapsed: Long) {
        val totalDist = _state.value.totalDistanceMeters
        val distanceKm = totalDist / 1000.0

        // Avg Speed
        val avgSpeedKmh = if (newElapsed > 0 && distanceKm > 0) (distanceKm / (newElapsed / 3600.0)) else null

        // Avg Pace
        val avgPaceMinPerKm = if (newElapsed > 10 && distanceKm > 0.05) (newElapsed / 60.0) / distanceKm else null

        // Avg HR
        val hrPoints = recordedPoints.mapNotNull { it.heartRateBpm }
        val avgHr = if (hrPoints.isNotEmpty()) hrPoints.average() else lastRecordedHeartRate?.toDouble()

        // Calories estimate
        val calories = estimateCalories(_state.value.exerciseType, totalDist, newElapsed)

        _state.value = _state.value.copy(
            elapsedSeconds = newElapsed,
            avgSpeedKmh = avgSpeedKmh,
            avgPaceFormatted = WorkoutDataPoint.formatPace(avgPaceMinPerKm),
            avgHeartRateBpm = avgHr,
            caloriesKcal = calories
        )
    }

    private fun estimateCalories(type: ExerciseType, distanceM: Double, seconds: Long): Double {
        val met = when (type) {
            ExerciseType.RUNNING, ExerciseType.TREADMILL -> 9.8
            ExerciseType.CYCLING, ExerciseType.INDOOR_CYCLING -> 7.5
            ExerciseType.WALKING, ExerciseType.HIKING -> 3.8
            ExerciseType.SWIMMING -> 8.0
            else -> 6.0
        }
        val assumedWeightKg = 70.0
        val hours = seconds / 3600.0
        return met * assumedWeightKg * hours
    }

    fun stopWorkout(): WorkoutSession? {
        if (!_state.value.isTracking) return null

        timerJob?.cancel()
        liveCsvWriter?.close()
        liveCsvWriter = null

        val end = Instant.now()
        val start = sessionStartTime ?: end.minusSeconds(_state.value.elapsedSeconds)

        val finalSummary = WorkoutSummary(
            totalDistanceMeters = _state.value.totalDistanceMeters,
            totalDurationSeconds = _state.value.elapsedSeconds,
            activeDurationSeconds = _state.value.elapsedSeconds,
            avgHeartRateBpm = _state.value.avgHeartRateBpm,
            maxHeartRateBpm = recordedPoints.mapNotNull { it.heartRateBpm }.maxOrNull(),
            minHeartRateBpm = recordedPoints.mapNotNull { it.heartRateBpm }.minOrNull(),
            avgSpeedKmh = _state.value.avgSpeedKmh,
            maxSpeedKmh = recordedPoints.mapNotNull { it.speedKmh }.maxOrNull(),
            avgPaceMinPerKm = if (_state.value.totalDistanceMeters > 50 && _state.value.elapsedSeconds > 10)
                (_state.value.elapsedSeconds / 60.0) / (_state.value.totalDistanceMeters / 1000.0) else null,
            totalCaloriesKcal = _state.value.caloriesKcal
        )

        val routePoints = recordedPoints.mapNotNull { pt ->
            if (pt.latitude != null && pt.longitude != null) {
                GeoPoint(pt.latitude, pt.longitude, pt.altitudeMeters, pt.timestamp)
            } else null
        }

        val session = WorkoutSession(
            id = currentWorkoutId,
            source = WorkoutSource.LIVE_TRACKER,
            exerciseType = _state.value.exerciseType,
            title = "Live ${_state.value.exerciseType.displayName} Session",
            startTime = start,
            endTime = end,
            summary = finalSummary,
            liveDataPoints = recordedPoints.toList(),
            routePoints = routePoints
        )

        // Reset state
        _state.value = LiveWorkoutState()
        lastLocation = null
        lastRecordedHeartRate = null

        return session
    }
}
