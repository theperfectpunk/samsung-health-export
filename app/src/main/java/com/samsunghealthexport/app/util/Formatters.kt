package com.samsunghealthexport.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {

    private val DATE_TIME_DISPLAY = DateTimeFormatter.ofPattern("MMM dd, yyyy • hh:mm a")
    private val DATE_DISPLAY = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy")
    private val TIME_DISPLAY = DateTimeFormatter.ofPattern("hh:mm a")

    fun formatDateTime(instant: Instant): String {
        return instant.atZone(ZoneId.systemDefault()).format(DATE_TIME_DISPLAY)
    }

    fun formatDate(instant: Instant): String {
        return instant.atZone(ZoneId.systemDefault()).format(DATE_DISPLAY)
    }

    fun formatTime(instant: Instant): String {
        return instant.atZone(ZoneId.systemDefault()).format(TIME_DISPLAY)
    }

    fun formatDuration(seconds: Long): String {
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hrs > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }

    fun formatDistance(meters: Double): String {
        val km = meters / 1000.0
        return String.format(Locale.US, "%.2f km", km)
    }

    fun formatSpeed(kmh: Double?): String {
        if (kmh == null || kmh <= 0) return "-- km/h"
        return String.format(Locale.US, "%.1f km/h", kmh)
    }

    fun formatHeartRate(bpm: Int?): String {
        if (bpm == null || bpm <= 0) return "-- bpm"
        return "$bpm bpm"
    }

    fun formatCalories(kcal: Double?): String {
        if (kcal == null || kcal <= 0) return "-- kcal"
        return String.format(Locale.US, "%.0f kcal", kcal)
    }
}
