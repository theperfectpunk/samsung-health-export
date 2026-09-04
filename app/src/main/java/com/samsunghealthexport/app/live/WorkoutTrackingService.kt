package com.samsunghealthexport.app.live

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.samsunghealthexport.app.MainActivity
import com.samsunghealthexport.app.R
import com.samsunghealthexport.app.model.ExerciseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class WorkoutTrackingService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val EXTRA_EXERCISE_TYPE = "EXTRA_EXERCISE_TYPE"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "workout_tracking_channel"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val tracker = LiveWorkoutTracker.instance
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val typeName = intent.getStringExtra(EXTRA_EXERCISE_TYPE) ?: ExerciseType.RUNNING.name
                val exerciseType = try { ExerciseType.valueOf(typeName) } catch (e: Exception) { ExerciseType.RUNNING }
                startWorkoutTracking(exerciseType)
            }
            ACTION_PAUSE -> tracker.pauseWorkout()
            ACTION_RESUME -> tracker.resumeWorkout()
            ACTION_STOP -> stopWorkoutTracking()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startWorkoutTracking(exerciseType: ExerciseType) {
        tracker.startWorkout(this, exerciseType)

        val notification = buildNotification("Starting workout...", "Acquiring GPS signal")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0)
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Setup GPS request: 1 second interval, high accuracy
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(1000)
            .setMinUpdateDistanceMeters(1.0f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        observeState()
    }

    private fun observeState() {
        stateObserverJob?.cancel()
        stateObserverJob = serviceScope.launch {
            tracker.state.collectLatest { s ->
                if (s.isTracking) {
                    val distKm = "%.2f km".format(s.totalDistanceMeters / 1000.0)
                    val hrText = s.currentHeartRateBpm?.let { "$it bpm" } ?: "-- bpm"
                    val content = "$distKm | Pace: ${s.currentPaceFormatted} | HR: $hrText"
                    val subText = "Avg Pace: ${s.avgPaceFormatted} | Live CSV Exporting"
                    updateNotification(content, subText)
                }
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                tracker.onLocationUpdate(location)
            }
        }
    }

    private fun stopWorkoutTracking() {
        stateObserverJob?.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignore
        }
        tracker.stopWorkout()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Workout Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live workout statistics and GPS tracking status"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildNotification(title, content)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stateObserverJob?.cancel()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
