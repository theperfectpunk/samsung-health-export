package com.samsunghealthexport.app.archive

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
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TreeMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object SamsungHealthArchiveParser {

    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /**
     * Parses a Samsung Health Personal Data ZIP file directly from an InputStream.
     * Streams through the zip, collects exercise summaries, and pairs them with their
     * live_data and location_data JSON files.
     */
    suspend fun parseZip(zipStream: InputStream): List<WorkoutSession> = withContext(Dispatchers.IO) {
        val zip = ZipInputStream(zipStream)
        var exerciseCsvContent: String? = null
        val liveDataFiles = mutableMapOf<String, String>() // uuid -> json string
        val locationDataFiles = mutableMapOf<String, String>() // uuid -> json string

        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name.contains("com.samsung.shealth.exercise") && name.endsWith(".csv")) {
                exerciseCsvContent = readStreamToString(zip)
            } else if (name.contains("live_data") && name.endsWith(".json")) {
                val uuid = extractUuid(name)
                if (uuid != null) {
                    liveDataFiles[uuid] = readStreamToString(zip)
                }
            } else if (name.contains("location_data") && name.endsWith(".json")) {
                val uuid = extractUuid(name)
                if (uuid != null) {
                    locationDataFiles[uuid] = readStreamToString(zip)
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }

        if (exerciseCsvContent == null) {
            return@withContext emptyList()
        }

        parseExerciseCsv(exerciseCsvContent, liveDataFiles, locationDataFiles)
    }

    private fun extractUuid(path: String): String? {
        val filename = path.substringAfterLast("/")
        // Match patterns like <uuid>.live_data.json or com.samsung.health.exercise.<uuid>.live_data.json
        val regex = Regex("([a-fA-F0-9\\-]{36})")
        return regex.find(filename)?.value
    }

    private fun readStreamToString(stream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val sb = StringBuilder()
        var line: String?
        val buffer = CharArray(8192)
        var read: Int
        while (reader.read(buffer).also { read = it } > 0) {
            sb.append(buffer, 0, read)
        }
        return sb.toString()
    }

    /**
     * Parses the main `com.samsung.shealth.exercise.csv` content and correlates with JSON maps.
     */
    fun parseExerciseCsv(
        csvContent: String,
        liveDataMap: Map<String, String>,
        locationDataMap: Map<String, String>
    ): List<WorkoutSession> {
        val lines = csvContent.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        // Find header line (Samsung Health CSVs sometimes have metadata in line 1)
        var headerIndex = 0
        while (headerIndex < lines.size && !lines[headerIndex].contains("datauuid")) {
            headerIndex++
        }
        if (headerIndex >= lines.size) return emptyList()

        val headers = parseCsvLine(lines[headerIndex])
        val uuidCol = headers.indexOf("datauuid").takeIf { it >= 0 } ?: headers.indexOf("uuid")
        val typeCol = headers.indexOf("exercise_type")
        val startCol = headers.indexOf("start_time")
        val endCol = headers.indexOf("end_time")
        val durationCol = headers.indexOf("duration")
        val distCol = headers.indexOf("distance")
        val calCol = headers.indexOf("calorie")
        val meanHrCol = headers.indexOf("mean_heart_rate")
        val maxHrCol = headers.indexOf("max_heart_rate")
        val minHrCol = headers.indexOf("min_heart_rate")
        val meanSpeedCol = headers.indexOf("mean_speed")
        val maxSpeedCol = headers.indexOf("max_speed")
        val meanCadenceCol = headers.indexOf("mean_cadence")
        val maxCadenceCol = headers.indexOf("max_cadence")
        val altGainCol = headers.indexOf("altitude_gain")
        val altLossCol = headers.indexOf("altitude_loss")
        val commentCol = headers.indexOf("comment")

        val workouts = mutableListOf<WorkoutSession>()

        for (i in (headerIndex + 1) until lines.size) {
            val cols = parseCsvLine(lines[i])
            if (cols.size <= uuidCol || uuidCol < 0) continue

            val uuid = cols[uuidCol]
            val typeCode = cols.getOrNull(typeCol)?.toIntOrNull() ?: 0
            val exerciseType = ExerciseType.fromSamsungCode(typeCode)

            val startTime = parseTimestamp(cols.getOrNull(startCol)) ?: Instant.now()
            val endTime = parseTimestamp(cols.getOrNull(endCol)) ?: startTime.plusSeconds(1800)
            val durationMs = cols.getOrNull(durationCol)?.toLongOrNull() ?: 0L
            val durationSec = if (durationMs > 0) durationMs / 1000 else (endTime.epochSecond - startTime.epochSecond)
            val distanceM = cols.getOrNull(distCol)?.toDoubleOrNull() ?: 0.0
            val calories = cols.getOrNull(calCol)?.toDoubleOrNull()
            val meanHr = cols.getOrNull(meanHrCol)?.toDoubleOrNull()
            val maxHr = cols.getOrNull(maxHrCol)?.toIntOrNull()
            val minHr = cols.getOrNull(minHrCol)?.toIntOrNull()
            val meanSpeed = cols.getOrNull(meanSpeedCol)?.toDoubleOrNull()
            val maxSpeed = cols.getOrNull(maxSpeedCol)?.toDoubleOrNull()
            val meanCadence = cols.getOrNull(meanCadenceCol)?.toIntOrNull()
            val altGain = cols.getOrNull(altGainCol)?.toDoubleOrNull()
            val altLoss = cols.getOrNull(altLossCol)?.toDoubleOrNull()
            val comment = cols.getOrNull(commentCol)

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

            // Parse Live Data & Location Data JSON for this UUID
            val liveJson = liveDataMap[uuid]
            val locationJson = locationDataMap[uuid]

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
                title = comment?.takeIf { it.isNotBlank() } ?: "${exerciseType.displayName} Export",
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
        val timeline = TreeMap<Long, MutableData>() // key: timestamp millis

        // Parse Live Data JSON
        if (!liveJson.isNullOrBlank()) {
            try {
                val array = JsonParser.parseString(liveJson).asJsonArray
                array.forEach { elem ->
                    val obj = elem.asJsonObject
                    val timeMs = obj.get("start_time")?.asLong ?: return@forEach
                    val data = timeline.getOrPut(timeMs) { MutableData(timeMs) }
                    data.speedMps = obj.get("speed")?.asDouble
                    data.cadenceSpm = obj.get("cadence")?.asInt
                    data.distanceMeters = obj.get("distance")?.asDouble
                    data.caloriesKcal = obj.get("calorie")?.asDouble
                    data.heartRateBpm = obj.get("heart_rate")?.asInt
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Parse Location Data JSON
        if (!locationJson.isNullOrBlank()) {
            try {
                val array = JsonParser.parseString(locationJson).asJsonArray
                array.forEach { elem ->
                    val obj = elem.asJsonObject
                    val timeMs = obj.get("start_time")?.asLong ?: return@forEach
                    val data = timeline.getOrPut(timeMs) { MutableData(timeMs) }
                    data.latitude = obj.get("latitude")?.asDouble
                    data.longitude = obj.get("longitude")?.asDouble
                    data.altitudeMeters = obj.get("altitude")?.asDouble
                    data.accuracyMeters = obj.get("accuracy")?.asFloat
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

    private fun parseTimestamp(str: String?): Instant? {
        if (str.isNullOrBlank()) return null
        return try {
            if (str.matches(Regex("\\d+"))) {
                Instant.ofEpochMilli(str.toLong())
            } else {
                LocalDateTime.parse(str, DATE_FORMATTER).atZone(ZoneId.systemDefault()).toInstant()
            }
        } catch (e: Exception) {
            try {
                Instant.parse(str)
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun parseCsvLine(line: String): List<String> {
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
