package com.samsunghealthexport.app.archive

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.GeoPoint
import com.samsunghealthexport.app.model.WorkoutDataPoint
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.model.WorkoutSource
import com.samsunghealthexport.app.model.WorkoutSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object SamsungHealthArchiveParser {

    private const val TAG = "SamsungHealthArchiveParser"

    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
        DateTimeFormatter.ISO_INSTANT
    )

    private val UUID_REGEX = Regex("([a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}|[a-fA-F0-9]{32})")

    /**
     * Parses a Samsung Health Personal Data ZIP file directly from an InputStream.
     * Searches for any exercise summary CSV and pairs each workout with its
     * corresponding live_data and location_data files.
     */
    suspend fun parseZip(zipStream: InputStream): List<WorkoutSession> = withContext(Dispatchers.IO) {
        val zip = ZipInputStream(zipStream)
        val exerciseCsvContents = mutableListOf<String>()
        val liveDataFiles = mutableMapOf<String, String>() // uuid -> json string
        val locationDataFiles = mutableMapOf<String, String>() // uuid -> json string

        try {
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase(Locale.US)

                // Match Samsung Health exercise summary CSV files
                val isExerciseCsv = name.endsWith(".csv") &&
                        (name.contains("exercise") || name.contains("shealth") || name.contains("health")) &&
                        !name.contains("live") && !name.contains("location")

                // Match live data files (supports both .json and raw format, live_data and live.data)
                val isLiveData = (name.contains("live_data") || name.contains("live.data"))

                // Match location data files (supports both .json and raw format, location_data and location.data)
                val isLocationData = (name.contains("location_data") || name.contains("location.data"))

                if (isExerciseCsv) {
                    Log.i(TAG, "Found exercise summary CSV: ${entry.name}")
                    exerciseCsvContents.add(readStreamToString(zip))
                } else if (isLiveData) {
                    val uuid = extractUuid(entry.name)
                    if (uuid != null) {
                        liveDataFiles[uuid.lowercase(Locale.US)] = readStreamToString(zip)
                    }
                } else if (isLocationData) {
                    val uuid = extractUuid(entry.name)
                    if (uuid != null) {
                        locationDataFiles[uuid.lowercase(Locale.US)] = readStreamToString(zip)
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading ZIP entries", e)
        }

        if (exerciseCsvContents.isEmpty()) {
            Log.w(TAG, "No exercise summary CSV found in ZIP")
            return@withContext emptyList()
        }

        Log.i(TAG, "Found ${exerciseCsvContents.size} CSVs, ${liveDataFiles.size} live data files, ${locationDataFiles.size} location files")

        // Parse all exercise CSVs found and combine workouts
        val allWorkouts = mutableListOf<WorkoutSession>()
        for (csvContent in exerciseCsvContents) {
            val workouts = parseExerciseCsv(csvContent, liveDataFiles, locationDataFiles)
            allWorkouts.addAll(workouts)
        }

        allWorkouts.distinctBy { it.id }.sortedByDescending { it.startTime }
    }

    fun extractUuid(path: String): String? {
        val filename = path.substringAfterLast("/")
        return UUID_REGEX.find(filename)?.value
    }

    private fun readStreamToString(stream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val sb = StringBuilder()
        val buffer = CharArray(8192)
        var read: Int
        while (reader.read(buffer).also { read = it } > 0) {
            sb.append(buffer, 0, read)
        }
        return sb.toString()
    }

    /**
     * Parses the main exercise CSV content and correlates with JSON maps.
     */
    fun parseExerciseCsv(
        csvContent: String,
        liveDataMap: Map<String, String>,
        locationDataMap: Map<String, String>
    ): List<WorkoutSession> {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        // Find header line (Samsung Health CSVs sometimes have metadata before column headers)
        var headerIndex = 0
        while (headerIndex < lines.size) {
            val lower = lines[headerIndex].lowercase(Locale.US)
            if (lower.contains("datauuid") || lower.contains("exercise_type") || lower.contains("start_time")) {
                break
            }
            headerIndex++
        }
        if (headerIndex >= lines.size) return emptyList()

        val rawHeaders = parseCsvLine(lines[headerIndex])
        val headers = rawHeaders.map { it.trim().lowercase(Locale.US).replace("\"", "").replace(" ", "_") }

        val uuidCol = headers.indexOfFirst { it == "datauuid" || it == "uuid" || it.contains("datauuid") }
        val typeCol = headers.indexOfFirst { it == "exercise_type" || it == "type" }
        val startCol = headers.indexOfFirst { it == "start_time" || it == "start" }
        val endCol = headers.indexOfFirst { it == "end_time" || it == "end" }
        val durationCol = headers.indexOfFirst { it == "duration" }
        val distCol = headers.indexOfFirst { it == "distance" }
        val calCol = headers.indexOfFirst { it == "calorie" || it == "calories" }
        val meanHrCol = headers.indexOfFirst { it == "mean_heart_rate" || it == "avg_heart_rate" }
        val maxHrCol = headers.indexOfFirst { it == "max_heart_rate" }
        val minHrCol = headers.indexOfFirst { it == "min_heart_rate" }
        val meanSpeedCol = headers.indexOfFirst { it == "mean_speed" || it == "avg_speed" }
        val maxSpeedCol = headers.indexOfFirst { it == "max_speed" }
        val meanCadenceCol = headers.indexOfFirst { it == "mean_cadence" || it == "avg_cadence" }
        val altGainCol = headers.indexOfFirst { it == "altitude_gain" || it == "elevation_gain" }
        val altLossCol = headers.indexOfFirst { it == "altitude_loss" || it == "elevation_loss" }
        val commentCol = headers.indexOfFirst { it == "comment" || it == "custom" || it == "title" }

        val workouts = mutableListOf<WorkoutSession>()

        for (i in (headerIndex + 1) until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.isEmpty()) continue

            val uuid = if (uuidCol >= 0 && cols.size > uuidCol) cols[uuidCol].trim() else "workout_$i"
            val typeCode = if (typeCol >= 0 && cols.size > typeCol) cols[typeCol].toIntOrNull() ?: 0 else 0
            val exerciseType = ExerciseType.fromSamsungCode(typeCode)

            val startTime = if (startCol >= 0 && cols.size > startCol) parseTimestamp(cols[startCol]) ?: Instant.now() else Instant.now()
            val endTime = if (endCol >= 0 && cols.size > endCol) parseTimestamp(cols[endCol]) ?: startTime.plusSeconds(1800) else startTime.plusSeconds(1800)

            val durationMs = if (durationCol >= 0 && cols.size > durationCol) cols[durationCol].toLongOrNull() ?: 0L else 0L
            val durationSec = if (durationMs > 0) durationMs / 1000 else (endTime.epochSecond - startTime.epochSecond).coerceAtLeast(0)

            val distanceM = if (distCol >= 0 && cols.size > distCol) cols[distCol].toDoubleOrNull() ?: 0.0 else 0.0
            val calories = if (calCol >= 0 && cols.size > calCol) cols[calCol].toDoubleOrNull() else null
            val meanHr = if (meanHrCol >= 0 && cols.size > meanHrCol) cols[meanHrCol].toDoubleOrNull() else null
            val maxHr = if (maxHrCol >= 0 && cols.size > maxHrCol) cols[maxHrCol].toIntOrNull() else null
            val minHr = if (minHrCol >= 0 && cols.size > minHrCol) cols[minHrCol].toIntOrNull() else null
            val meanSpeed = if (meanSpeedCol >= 0 && cols.size > meanSpeedCol) cols[meanSpeedCol].toDoubleOrNull() else null
            val maxSpeed = if (maxSpeedCol >= 0 && cols.size > maxSpeedCol) cols[maxSpeedCol].toDoubleOrNull() else null
            val meanCadence = if (meanCadenceCol >= 0 && cols.size > meanCadenceCol) cols[meanCadenceCol].toIntOrNull() else null
            val altGain = if (altGainCol >= 0 && cols.size > altGainCol) cols[altGainCol].toDoubleOrNull() else null
            val altLoss = if (altLossCol >= 0 && cols.size > altLossCol) cols[altLossCol].toDoubleOrNull() else null
            val comment = if (commentCol >= 0 && cols.size > commentCol) cols[commentCol] else null

            val summary = WorkoutSummary(
                totalDistanceMeters = distanceM,
                totalDurationSeconds = durationSec,
                activeDurationSeconds = durationSec,
                avgHeartRateBpm = meanHr,
                maxHeartRateBpm = maxHr,
                minHeartRateBpm = minHr,
                avgSpeedKmh = meanSpeed?.let { it * 3.6 },
                maxSpeedKmh = maxSpeed?.let { it * 3.6 },
                avgPaceMinPerKm = if (distanceM > 100 && durationSec > 10) (durationSec / 60.0) / (distanceM / 1000.0) else null,
                totalCaloriesKcal = calories,
                elevationGainMeters = altGain,
                elevationLossMeters = altLoss,
                avgCadenceSpm = meanCadence
            )

            // Look up live data and location data using flexible UUID matching
            val cleanUuid = uuid.lowercase(Locale.US)
            val liveJson = liveDataMap[cleanUuid]
                ?: liveDataMap[cleanUuid.replace("-", "")]
                ?: liveDataMap.entries.firstOrNull { it.key.contains(cleanUuid) || cleanUuid.contains(it.key) }?.value

            val locationJson = locationDataMap[cleanUuid]
                ?: locationDataMap[cleanUuid.replace("-", "")]
                ?: locationDataMap.entries.firstOrNull { it.key.contains(cleanUuid) || cleanUuid.contains(it.key) }?.value

            val livePoints = parseGranularData(startTime, liveJson, locationJson)
            val routePoints = livePoints.mapNotNull { pt ->
                if (pt.latitude != null && pt.longitude != null) {
                    GeoPoint(pt.latitude, pt.longitude, pt.altitudeMeters, pt.timestamp)
                } else null
            }

            val session = WorkoutSession.buildWithCalculatedAverages(
                id = uuid,
                source = WorkoutSource.SAMSUNG_HEALTH_ARCHIVE,
                exerciseType = exerciseType,
                title = comment?.takeIf { it.isNotBlank() } ?: "${exerciseType.displayName} Session",
                startTime = startTime,
                endTime = endTime,
                existingSummary = summary,
                liveDataPoints = livePoints,
                routePoints = routePoints,
                notes = comment
            )

            workouts.add(session)
        }

        return workouts
    }

    private fun parseGranularData(
        sessionStart: Instant,
        liveJson: String?,
        locationJson: String?
    ): List<WorkoutDataPoint> {
        val timeline = TreeMap<Long, MutableData>()

        // Parse Live Data JSON
        if (!liveJson.isNullOrBlank()) {
            try {
                val element = JsonParser.parseString(liveJson)
                val array = if (element.isJsonArray) element.asJsonArray else JsonArray().apply { add(element) }
                array.forEach { elem ->
                    if (!elem.isJsonObject) return@forEach
                    val obj = elem.asJsonObject
                    val timeMs = obj.get("start_time")?.asLong
                        ?: obj.get("timestamp")?.asLong
                        ?: return@forEach
                    val data = timeline.getOrPut(timeMs) { MutableData(timeMs) }
                    data.speedMps = obj.get("speed")?.asDouble
                    data.cadenceSpm = obj.get("cadence")?.asInt
                    data.distanceMeters = obj.get("distance")?.asDouble
                    data.caloriesKcal = obj.get("calorie")?.asDouble ?: obj.get("calories")?.asDouble
                    data.heartRateBpm = obj.get("heart_rate")?.asInt ?: obj.get("heartRate")?.asInt
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing live data JSON", e)
            }
        }

        // Parse Location Data JSON
        if (!locationJson.isNullOrBlank()) {
            try {
                val element = JsonParser.parseString(locationJson)
                val array = if (element.isJsonArray) element.asJsonArray else JsonArray().apply { add(element) }
                array.forEach { elem ->
                    if (!elem.isJsonObject) return@forEach
                    val obj = elem.asJsonObject
                    val timeMs = obj.get("start_time")?.asLong
                        ?: obj.get("timestamp")?.asLong
                        ?: return@forEach
                    val data = timeline.getOrPut(timeMs) { MutableData(timeMs) }
                    data.latitude = obj.get("latitude")?.asDouble
                    data.longitude = obj.get("longitude")?.asDouble
                    data.altitudeMeters = obj.get("altitude")?.asDouble
                    data.accuracyMeters = obj.get("accuracy")?.asFloat
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing location data JSON", e)
            }
        }

        var cumulativeDistance = 0.0
        return timeline.values.map { d ->
            val timestamp = Instant.ofEpochMilli(d.timeMs)
            val elapsed = (timestamp.epochSecond - sessionStart.epochSecond).coerceAtLeast(0)
            if (d.distanceMeters != null) {
                cumulativeDistance += d.distanceMeters!!
            }
            val speedKmh = d.speedMps?.let { it * 3.6 }
            val pace = WorkoutDataPoint.calculatePaceMinPerKm(d.speedMps)

            WorkoutDataPoint(
                timestamp = timestamp,
                elapsedSeconds = elapsed,
                latitude = d.latitude,
                longitude = d.longitude,
                altitudeMeters = d.altitudeMeters,
                accuracyMeters = d.accuracyMeters,
                speedMps = d.speedMps,
                speedKmh = speedKmh,
                paceMinPerKm = pace,
                paceFormatted = WorkoutDataPoint.formatPace(pace),
                heartRateBpm = d.heartRateBpm,
                distanceMeters = d.distanceMeters,
                cumulativeDistanceMeters = cumulativeDistance,
                cadenceSpm = d.cadenceSpm,
                caloriesKcal = d.caloriesKcal
            )
        }
    }

    fun parseTimestamp(str: String?): Instant? {
        if (str.isNullOrBlank()) return null
        val trimmed = str.trim().replace("\"", "")

        // 1. Numeric epoch millis
        if (trimmed.matches(Regex("^\\d+$"))) {
            return try {
                Instant.ofEpochMilli(trimmed.toLong())
            } catch (e: Exception) {
                null
            }
        }

        // 2. Try date time patterns
        for (formatter in DATE_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
            } catch (ignored: Exception) {
            }
        }

        // 3. Try Instant.parse
        return try {
            Instant.parse(trimmed)
        } catch (e: Exception) {
            null
        }
    }

    fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '\"') {
                    current.append('\"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim())
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim())
        return result
    }

    private class MutableData(val timeMs: Long) {
        var latitude: Double? = null
        var longitude: Double? = null
        var altitudeMeters: Double? = null
        var accuracyMeters: Float? = null
        var speedMps: Double? = null
        var cadenceSpm: Int? = null
        var distanceMeters: Double? = null
        var caloriesKcal: Double? = null
        var heartRateBpm: Int? = null
    }
}
