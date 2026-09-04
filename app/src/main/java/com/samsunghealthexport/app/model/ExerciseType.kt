package com.samsunghealthexport.app.model

enum class ExerciseType(val displayName: String, val iconName: String) {
    RUNNING("Running", "directions_run"),
    WALKING("Walking", "directions_walk"),
    CYCLING("Cycling", "directions_bike"),
    HIKING("Hiking", "hiking"),
    SWIMMING("Swimming", "pool"),
    TREADMILL("Treadmill Running", "fitness_center"),
    INDOOR_CYCLING("Indoor Cycling", "pedal_bike"),
    ROWING("Rowing", "rowing"),
    ELLIPTICAL("Elliptical", "fitness_center"),
    STRENGTH_TRAINING("Strength Training", "fitness_center"),
    YOGA("Yoga", "self_improvement"),
    AEROBICS("Aerobics", "sports_gymnastics"),
    OTHER("Workout", "fitness_center");

    companion object {
        fun fromSamsungCode(code: Int): ExerciseType {
            return when (code) {
                1001, 1002 -> RUNNING
                1003 -> WALKING
                11007, 1004 -> CYCLING
                13001, 1006 -> HIKING
                14001, 1007 -> SWIMMING
                10001 -> TREADMILL
                11001 -> INDOOR_CYCLING
                10011 -> ROWING
                10012 -> ELLIPTICAL
                else -> OTHER
            }
        }

        fun fromHealthConnectType(type: Int): ExerciseType {
            // HealthConnect ExerciseSessionRecord exercise type mapping
            return when (type) {
                56 -> RUNNING // EXERCISE_TYPE_RUNNING
                57 -> TREADMILL // EXERCISE_TYPE_RUNNING_TREADMILL
                79 -> WALKING // EXERCISE_TYPE_WALKING
                8 -> CYCLING // EXERCISE_TYPE_BIKING
                9 -> INDOOR_CYCLING // EXERCISE_TYPE_BIKING_STATIONARY
                37 -> HIKING // EXERCISE_TYPE_HIKING
                71 -> SWIMMING // EXERCISE_TYPE_SWIMMING_POOL
                72 -> SWIMMING // EXERCISE_TYPE_SWIMMING_OPEN_WATER
                68 -> ROWING // EXERCISE_TYPE_ROWING_MACHINE
                25 -> ELLIPTICAL // EXERCISE_TYPE_ELLIPTICAL
                70 -> STRENGTH_TRAINING // EXERCISE_TYPE_STRENGTH_TRAINING
                84 -> YOGA // EXERCISE_TYPE_YOGA
                else -> OTHER
            }
        }
    }
}
