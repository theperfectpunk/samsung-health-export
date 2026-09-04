package com.samsunghealthexport.app.export

import com.samsunghealthexport.app.model.ExportFormat
import com.samsunghealthexport.app.model.WorkoutDataPoint
import com.samsunghealthexport.app.model.WorkoutSession
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.format.DateTimeFormatter
import java.util.Locale

object CsvExporter {

    private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

    /**
     * Generates CSV string based on selected format.
     */
    fun exportWorkout(session: WorkoutSession, format: ExportFormat, delimiter: String = ","): String {
        return when (format) {
            ExportFormat.COMBINED_CSV -> buildCombinedCsv(session, delimiter)
            ExportFormat.LIVE_DATA_ONLY_CSV -> buildLiveDataOnlyCsv(session, delimiter)
            ExportFormat.SUMMARY_ONLY_CSV -> buildSummaryOnlyCsv(listOf(session), delimiter)
            ExportFormat.GPX -> GpxExporter.buildGpx(session)
        }
    }

    /**
     * Exports a collection of sessions to a multi-row summary CSV.
     */
    fun exportMultiSummary(sessions: List<WorkoutSession>, delimiter: String = ","): String {
        return buildSummaryOnlyCsv(sessions, delimiter)
    }

    /**
     * Combined CSV: Metadata & Averages block first, followed by granular time-series data.
     */
    private fun buildCombinedCsv(session: WorkoutSession, d: String): String {
        val sb = StringBuilder()
        val s = session.summary

        sb.append("# ========================================================\n")
        sb.append("# SAMSUNG HEALTH WORKOUT EXPORT - SESSION SUMMARY & AVERAGES\n")
        sb.append("# ========================================================\n")
        sb.append("# Title${d}${escape(session.title)}\n")
        sb.append("# Sport${d}${session.exerciseType.displayName}\n")
        sb.append("# Source${d}${session.source.label}\n")
        sb.append("# Start Time${d}${ISO_FORMATTER.format(session.startTime)}\n")
        sb.append("# End Time${d}${ISO_FORMATTER.format(session.endTime)}\n")
        sb.append("# Total Duration (HH:mm:ss)${d}${s.formattedDuration}\n")
        sb.append("# Total Duration (Seconds)${d}${s.totalDurationSeconds}\n")
        sb.append("# Active Duration (Seconds)${d}${s.activeDurationSeconds ?: s.totalDurationSeconds}\n")
        sb.append("# Total Distance (m)${d}${formatDouble(s.totalDistanceMeters, 2)}\n")
        sb.append("# Total Distance (km)${d}${formatDouble(s.totalDistanceKm, 3)}\n")
        sb.append("# Average Heart Rate (bpm)${d}${s.avgHeartRateBpm?.let { formatDouble(it, 1) } ?: "--"}\n")
        sb.append("# Max Heart Rate (bpm)${d}${s.maxHeartRateBpm ?: "--"}\n")
        sb.append("# Min Heart Rate (bpm)${d}${s.minHeartRateBpm ?: "--"}\n")
        sb.append("# Average Speed (km/h)${d}${s.avgSpeedKmh?.let { formatDouble(it, 2) } ?: "--"}\n")
        sb.append("# Max Speed (km/h)${d}${s.maxSpeedKmh?.let { formatDouble(it, 2) } ?: "--"}\n")
        sb.append("# Average Pace (min/km)${d}${s.avgPaceMinPerKm?.let { formatDouble(it, 2) } ?: "--"}\n")
        sb.append("# Average Pace Formatted${d}${s.formattedAvgPace}\n")
        sb.append("# Best Pace Formatted${d}${s.formattedBestPace}\n")
        sb.append("# Total Calories (kcal)${d}${s.totalCaloriesKcal?.let { formatDouble(it, 1) } ?: "--"}\n")
        sb.append("# Elevation Gain (m)${d}${s.elevationGainMeters?.let { formatDouble(it, 1) } ?: "--"}\n")
        sb.append("# Elevation Loss (m)${d}${s.elevationLossMeters?.let { formatDouble(it, 1) } ?: "--"}\n")
        sb.append("# Total Steps${d}${s.totalSteps ?: "--"}\n")
        sb.append("# Average Cadence (spm)${d}${s.avgCadenceSpm ?: "--"}\n")
        sb.append("# Total Live Data Points${d}${session.liveDataPoints.size}\n")
        sb.append("# ========================================================\n")
        sb.append("# LIVE GRANULAR DATA DURING WORKOUT\n")
        sb.append("# ========================================================\n")

        // Column headers
        sb.append(getLiveColumnsHeader(d)).append("\n")

        // Data rows
        session.liveDataPoints.forEach { pt ->
            sb.append(formatDataPointRow(pt, d)).append("\n")
        }

        return sb.toString()
    }

    /**
     * Live Data Only CSV: Pure tabular data format for analysis in pandas, R, Excel.
     */
    private fun buildLiveDataOnlyCsv(session: WorkoutSession, d: String): String {
        val sb = StringBuilder()
        sb.append(getLiveColumnsHeader(d)).append("\n")
        session.liveDataPoints.forEach { pt ->
            sb.append(formatDataPointRow(pt, d)).append("\n")
        }
        return sb.toString()
    }

    /**
     * Multi-row summary CSV for one or more workouts.
     */
    private fun buildSummaryOnlyCsv(sessions: List<WorkoutSession>, d: String): String {
        val sb = StringBuilder()
        sb.append(
            listOf(
                "Workout_ID",
                "Title",
                "Sport",
                "Source",
                "Start_Time",
                "End_Time",
                "Duration_sec",
                "Duration_formatted",
                "Distance_m",
                "Distance_km",
                "Avg_Heart_Rate_bpm",
                "Max_Heart_Rate_bpm",
                "Min_Heart_Rate_bpm",
                "Avg_Speed_kmh",
                "Max_Speed_kmh",
                "Avg_Pace_min_per_km",
                "Avg_Pace_formatted",
                "Best_Pace_formatted",
                "Calories_kcal",
                "Elevation_Gain_m",
                "Total_Steps",
                "Avg_Cadence_spm",
                "Live_Points_Count"
            ).joinToString(d)
        ).append("\n")

        sessions.forEach { s ->
            val sm = s.summary
            sb.append(
                listOf(
                    escape(s.id),
                    escape(s.title),
                    escape(s.exerciseType.displayName),
                    escape(s.source.label),
                    ISO_FORMATTER.format(s.startTime),
                    ISO_FORMATTER.format(s.endTime),
                    sm.totalDurationSeconds.toString(),
                    escape(sm.formattedDuration),
                    formatDouble(sm.totalDistanceMeters, 2),
                    formatDouble(sm.totalDistanceKm, 3),
                    sm.avgHeartRateBpm?.let { formatDouble(it, 1) } ?: "",
                    sm.maxHeartRateBpm?.toString() ?: "",
                    sm.minHeartRateBpm?.toString() ?: "",
                    sm.avgSpeedKmh?.let { formatDouble(it, 2) } ?: "",
                    sm.maxSpeedKmh?.let { formatDouble(it, 2) } ?: "",
                    sm.avgPaceMinPerKm?.let { formatDouble(it, 2) } ?: "",
                    escape(sm.formattedAvgPace),
                    escape(sm.formattedBestPace),
                    sm.totalCaloriesKcal?.let { formatDouble(it, 1) } ?: "",
                    sm.elevationGainMeters?.let { formatDouble(it, 1) } ?: "",
                    sm.totalSteps?.toString() ?: "",
                    sm.avgCadenceSpm?.toString() ?: "",
                    s.liveDataPoints.size.toString()
                ).joinToString(d)
            ).append("\n")
        }

        return sb.toString()
    }

    private fun getLiveColumnsHeader(d: String): String {
        return listOf(
            "Timestamp",
            "Elapsed_Seconds",
            "Latitude",
            "Longitude",
            "Altitude_m",
            "Accuracy_m",
            "Speed_mps",
            "Speed_kmh",
            "Pace_min_per_km",
            "Pace_formatted",
            "Heart_Rate_bpm",
            "Interval_Distance_m",
            "Cumulative_Distance_m",
            "Cumulative_Distance_km",
            "Cadence_spm",
            "Calories_kcal",
            "Power_watts"
        ).joinToString(d)
    }

    fun formatDataPointRow(pt: WorkoutDataPoint, d: String): String {
        val cumDistKm = pt.cumulativeDistanceMeters?.let { it / 1000.0 }
        return listOf(
            ISO_FORMATTER.format(pt.timestamp),
            pt.elapsedSeconds.toString(),
            pt.latitude?.let { formatDouble(it, 7) } ?: "",
            pt.longitude?.let { formatDouble(it, 7) } ?: "",
            pt.altitudeMeters?.let { formatDouble(it, 2) } ?: "",
            pt.accuracyMeters?.let { formatDouble(it.toDouble(), 1) } ?: "",
            pt.speedMps?.let { formatDouble(it, 2) } ?: "",
            pt.speedKmh?.let { formatDouble(it, 2) } ?: "",
            pt.paceMinPerKm?.let { formatDouble(it, 2) } ?: "",
            pt.paceFormatted ?: (pt.paceMinPerKm?.let { WorkoutDataPoint.formatPace(it) } ?: ""),
            pt.heartRateBpm?.toString() ?: "",
            pt.distanceMeters?.let { formatDouble(it, 2) } ?: "",
            pt.cumulativeDistanceMeters?.let { formatDouble(it, 2) } ?: "",
            cumDistKm?.let { formatDouble(it, 3) } ?: "",
            pt.cadenceSpm?.toString() ?: "",
            pt.caloriesKcal?.let { formatDouble(it, 2) } ?: "",
            pt.powerWatts?.let { formatDouble(it, 1) } ?: ""
        ).joinToString(d)
    }

    /**
     * Real-time streaming CSV writer used by the live workout tracker.
     * Appends live telemetry points to disk during the active workout.
     */
    class LiveCsvWriter(private val targetFile: File, private val delimiter: String = ",") {
        private val writer: PrintWriter = PrintWriter(FileWriter(targetFile, true), true)

        init {
            if (targetFile.length() == 0L) {
                writer.println(getLiveColumnsHeader(delimiter))
            }
        }

        fun writePoint(point: WorkoutDataPoint) {
            writer.println(formatDataPointRow(point, delimiter))
        }

        fun close() {
            writer.flush()
            writer.close()
        }
    }

    private fun escape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun formatDouble(value: Double, decimals: Int): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }
}
