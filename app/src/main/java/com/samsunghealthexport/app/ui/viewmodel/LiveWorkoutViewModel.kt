package com.samsunghealthexport.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samsunghealthexport.app.live.BleHeartRateManager
import com.samsunghealthexport.app.live.LiveWorkoutState
import com.samsunghealthexport.app.live.LiveWorkoutTracker
import com.samsunghealthexport.app.live.WorkoutTrackingService
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.WorkoutSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LiveWorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val tracker = LiveWorkoutTracker.instance
    val bleManager = BleHeartRateManager(application)

    val liveState: StateFlow<LiveWorkoutState> = tracker.state
    val bleState = bleManager.connectionState
    val connectedDevice = bleManager.connectedDeviceName
    val liveHeartRate = bleManager.currentHeartRate

    init {
        // Wire BLE Heart Rate updates into Live Workout Tracker
        viewModelScope.launch {
            bleManager.currentHeartRate.collectLatest { hr ->
                if (hr != null) {
                    tracker.onHeartRateUpdate(hr)
                }
            }
        }
    }

    fun startWorkout(exerciseType: ExerciseType) {
        val app = getApplication<Application>()
        val intent = Intent(app, WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_START
            putExtra(WorkoutTrackingService.EXTRA_EXERCISE_TYPE, exerciseType.name)
        }
        app.startService(intent)
    }

    fun pauseWorkout() {
        val app = getApplication<Application>()
        val intent = Intent(app, WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_PAUSE
        }
        app.startService(intent)
    }

    fun resumeWorkout() {
        val app = getApplication<Application>()
        val intent = Intent(app, WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_RESUME
        }
        app.startService(intent)
    }

    fun finishWorkout(): WorkoutSession? {
        val app = getApplication<Application>()
        val intent = Intent(app, WorkoutTrackingService::class.java).apply {
            action = WorkoutTrackingService.ACTION_STOP
        }
        app.startService(intent)
        return tracker.stopWorkout()
    }

    fun startBleScan() {
        bleManager.startScan()
    }

    fun stopBleScan() {
        bleManager.stopScan()
    }

    fun disconnectBle() {
        bleManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.stopScan()
    }
}
