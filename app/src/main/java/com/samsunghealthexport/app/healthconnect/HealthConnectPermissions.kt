package com.samsunghealthexport.app.healthconnect

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord

object HealthConnectPermissions {

    const val READ_EXERCISE_ROUTES = "android.permission.health.READ_EXERCISE_ROUTES"
    const val READ_HEALTH_DATA_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"

    val REQUIRED_EXERCISE_PERMISSION = HealthPermission.getReadPermission(ExerciseSessionRecord::class)

    val ALL_PERMISSIONS = setOf(
        REQUIRED_EXERCISE_PERMISSION,
        READ_HEALTH_DATA_HISTORY,
        READ_EXERCISE_ROUTES,
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class)
    )

    // Legacy alias for compatibility
    val PERMISSIONS = ALL_PERMISSIONS
}
