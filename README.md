# Samsung Health Workout Exporter (Android)

A high-performance Android application built with **Kotlin** and **Jetpack Compose** that extracts running, cycling, walking, and other workout sessions from **Samsung Health** and exports them into clean, standardized **CSV** files containing granular **GPS track data, speed, instantaneous pace, heart rate series, cadence, and workout averages**.

It also features an **Active Live Workout Mode** that records real-time GPS coordinates and Bluetooth Low Energy (BLE) heart rate during your workout, displaying live telemetry gauges alongside running averages, and continuously writes the live data to a CSV file in real time.

---

## Key Features

### 1. Dual Samsung Health Extraction Methods
* **Automated Sync via Android Health Connect**:
  * Directly interfaces with Android's native Health Connect platform (`androidx.health.connect:connect-client`).
  * Seamlessly reads sessions synced from Samsung Health on Galaxy smartphones and Galaxy Watches:
    * `ExerciseSessionRecord` (Running, Cycling, Walking, Hiking, etc.)
    * `ExerciseRouteRecord` (High-precision GPS tracks: latitude, longitude, altitude, horizontal & vertical accuracy)
    * `HeartRateRecord` (Continuous BPM time-series)
    * `SpeedRecord` (Instantaneous speed in m/s and km/h)
    * `DistanceRecord` (Interval and cumulative distance)
    * `StepsRecord` & `Cadence` (Steps per minute)
    * `TotalCaloriesBurnedRecord` & `ActiveCaloriesBurnedRecord`
    * `ElevationGainedRecord` & `PowerRecord` (Watts)
  * Merges all discrete data streams into a synchronized second-by-second timeline.
* **Samsung Health Personal Data Archive (.zip) Importer**:
  * For offline data dumps or users without Health Connect sync:
  * In Samsung Health, tap **Settings > Download personal data**.
  * Import the resulting ZIP file directly into the app.
  * The app parses `com.samsung.shealth.exercise.csv` along with granular `jsons/com.samsung.shealth.exercise/*.live_data.json` and `*.location_data.json`, synchronizing GPS and telemetry points by timestamp.

### 2. Live Workout Tracker & Streaming CSV Exporter
* **Real-time Live Telemetry**:
  * Foreground Service (`WorkoutTrackingService`) with persistent status notification and wake lock to ensure uninterrupted tracking when the screen is turned off or locked.
  * 1-second interval GPS sampling with speed smoothing and live pace ($min/km$) calculation.
  * Built-in Bluetooth Low Energy (BLE) Heart Rate monitor support (standard GATT Heart Rate Service `0x180D`, compatible with Galaxy Watch broadcasts, Polar, Garmin, Wahoo).
* **Live Streaming to CSV**:
  * As you exercise, every data point is appended to an active CSV file on disk. If your battery drains or the workout is interrupted, your data is preserved.
  * Live dashboard displaying:
    * **Current Speed** ($km/h$) & **Average Speed** ($km/h$)
    * **Current Pace** ($min/km$) & **Average Pace** ($min/km$)
    * **Current Heart Rate** ($bpm$) with pulsing animation & **Average Heart Rate** ($bpm$)
    * **Total Distance** ($km$) & **Active Duration** ($HH:MM:SS$) & **Calories** ($kcal$)
  * On workout completion, automatically compiles summary averages into the header and opens the export share sheet.

### 3. Multiple Export Formats
* **Combined CSV (Summary + Live Track)**:
  * Includes a detailed metadata header comment block with all session averages (average HR, max HR, min HR, average speed, max speed, average pace, best pace, elevation gain, total calories, total steps, average cadence).
  * Followed by the synchronized tabular time-series with columns:
    `Timestamp`, `Elapsed_Seconds`, `Latitude`, `Longitude`, `Altitude_m`, `Accuracy_m`, `Speed_mps`, `Speed_kmh`, `Pace_min_per_km`, `Pace_formatted`, `Heart_Rate_bpm`, `Interval_Distance_m`, `Cumulative_Distance_m`, `Cumulative_Distance_km`, `Cadence_spm`, `Calories_kcal`, `Power_watts`.
* **Live Track Only CSV (Raw Time-Series)**:
  * Clean, headless or pure tabular CSV optimized for direct ingestion into Python (`pandas.read_csv`), Excel, R, or data science workflows.
* **Summary Only CSV**:
  * Multi-workout overview table containing 1 row per workout with all aggregate stats and averages.
* **GPX with Garmin/Strava Extensions**:
  * Standard GPX 1.1 with `<gpxtpx:TrackPointExtension>` including `<gpxtpx:hr>` and `<gpxtpx:speed>` for direct import into Strava, Garmin Connect, or Google Earth.

---

## Project Structure

```
health-export/
├── app/
│   ├── build.gradle.kts                   # Dependencies & Android configuration
│   ├── proguard-rules.pro                 # Health Connect reflection rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Health Connect, GPS, BLE permissions & services
│       │   ├── res/                       # Theme, colors, strings, file_paths
│       │   └── java/com/samsunghealthexport/app/
│       │       ├── MainActivity.kt        # Compose UI host & permission manager
│       │       ├── model/                 # Data models (WorkoutSession, WorkoutDataPoint, etc.)
│       │       ├── healthconnect/         # Health Connect Client & automated sync
│       │       ├── archive/               # Samsung Health ZIP archive parser
│       │       ├── live/                  # Foreground GPS service, BLE HR manager, Live Tracker
│       │       ├── export/                # CsvExporter, GpxExporter, ExportFileManager
│       │       ├── ui/
│       │       │   ├── components/        # WorkoutCard, MetricStatCard, LiveMetricGauge
│       │       │   ├── screens/           # WorkoutsList, WorkoutDetail, LiveWorkout, Import, Settings
│       │       │   ├── theme/             # Dark athletic theme, colors, typography
│       │       │   └── viewmodel/         # WorkoutsViewModel, LiveWorkoutViewModel
│       │       └── util/                  # Time formatters, pace calculations, math
│       └── test/java/com/samsunghealthexport/app/
│           ├── CsvExporterTest.kt         # CSV formatting & streaming writer unit tests
│           ├── SamsungHealthArchiveParserTest.kt  # JSON & CSV parsing unit tests
│           └── MetricCalculationsTest.kt  # Pace & duration conversion tests
├── gradle/
│   ├── libs.versions.toml                 # Version catalog
│   └── wrapper/                           # Gradle Wrapper 8.8
├── build.gradle.kts                       # Root build configuration
├── settings.gradle.kts                    # Root project settings
└── README.md
```

---

## CSV Export Schema

### 1. Header Section (Combined CSV)
```csv
# ========================================================
# SAMSUNG HEALTH WORKOUT EXPORT - SESSION SUMMARY & AVERAGES
# ========================================================
# Title,Morning 5K Run
# Sport,Running
# Source,Samsung Health (Health Connect)
# Start Time,2026-09-04T07:00:00Z
# End Time,2026-09-04T07:28:45Z
# Total Duration (HH:mm:ss),00:28:45
# Total Duration (Seconds),1725
# Active Duration (Seconds),1725
# Total Distance (m),5240.50
# Total Distance (km),5.241
# Average Heart Rate (bpm),152.4
# Max Heart Rate (bpm),176
# Min Heart Rate (bpm),104
# Average Speed (km/h),10.94
# Max Speed (km/h),13.80
# Average Pace (min/km),05:29
# Average Pace Formatted,5'29"/km
# Best Pace Formatted,4'21"/km
# Total Calories (kcal),385.0
# Elevation Gain (m),42.5
# Elevation Loss (m),38.0
# Total Steps,4320
# Average Cadence (spm),162
# Total Live Data Points,1725
# ========================================================
# LIVE GRANULAR DATA DURING WORKOUT
# ========================================================
```

### 2. Time-Series Columns (Live Granular Data)
| Column Name | Type | Description |
| :--- | :--- | :--- |
| `Timestamp` | ISO 8601 String | UTC timestamp of sample (`2026-09-04T07:00:00Z`) |
| `Elapsed_Seconds` | Integer | Seconds elapsed since workout start |
| `Latitude` | Float (7 decimals) | GPS Latitude in degrees |
| `Longitude` | Float (7 decimals) | GPS Longitude in degrees |
| `Altitude_m` | Float (meters) | GPS / Barometric altitude above sea level |
| `Accuracy_m` | Float (meters) | GPS horizontal accuracy radius |
| `Speed_mps` | Float (m/s) | Instantaneous velocity in meters per second |
| `Speed_kmh` | Float (km/h) | Instantaneous velocity in kilometers per hour |
| `Pace_min_per_km`| Float (min/km)| Instantaneous pace in decimal minutes per km |
| `Pace_formatted` | String | Instantaneous pace formatted as `MM'SS"/km` |
| `Heart_Rate_bpm` | Integer | Heart rate in beats per minute |
| `Interval_Distance_m` | Float (m) | Distance covered in this time interval |
| `Cumulative_Distance_m` | Float (m) | Total cumulative distance in meters |
| `Cumulative_Distance_km`| Float (km) | Total cumulative distance in kilometers |
| `Cadence_spm` | Integer | Steps per minute (or pedal RPM for cycling) |
| `Calories_kcal` | Float (kcal) | Cumulative or interval energy expended |
| `Power_watts` | Float (W) | Running or cycling power in watts |

---

## How to Set Up & Use

### 1. Enabling Samsung Health Sync with Health Connect
1. On your Samsung Galaxy phone, open **Samsung Health**.
2. Tap **Settings** (gear icon) > **Health Connect**.
3. Under **Permissions**, enable:
   * **Read permissions**: Exercise, Distance, Heart Rate, Speed, Steps, Total Calories, Elevation, Power, and Routes.
4. Open **Samsung Health Exporter** and grant the Health Connect permissions prompt.
5. All workouts recorded on your phone or Galaxy Watch will be automatically listed!

### 2. Importing Samsung Health Personal Data ZIP
1. Open **Samsung Health** > **Settings** > **Download personal data**.
2. Tap **Request**. Once prepared, download the ZIP archive.
3. In **Samsung Health Exporter**, open the **Import** tab.
4. Tap **Select Samsung Health ZIP Archive** and choose the downloaded file.
5. The app automatically extracts the exercise summaries and links each workout's `live_data.json` (speed, cadence, heart rate) and `location_data.json` (GPS coordinates).

### 3. Recording a Live Workout
1. Open the **Live Tracker** tab.
2. Select your sport (Running, Cycling, Walking).
3. *(Optional)* Tap **Connect HR** to pair your Bluetooth Heart Rate monitor or Galaxy Watch BLE broadcast.
4. Tap **START WORKOUT**.
5. The app launches a Foreground Service with persistent GPS tracking and displays live gauges for speed, pace, heart rate, and running averages.
6. The app continuously appends rows to a live CSV file on storage.
7. Tap **Finish & Export** to complete the workout and immediately share or save the combined CSV.

---

## Download APK & Releases

Pre-built APKs are automatically compiled and published to GitHub Releases by our CI/CD pipeline:

👉 **[Download Latest APK from Releases](https://github.com/theperfectpunk/samsung-health-export/releases)**

---

## Building from Source

### Prerequisites
* JDK 17+
* Android SDK (API 34 compileSdk, API 26 minSdk)

### Command-line Build
```bash
cd health-export

# Run unit tests
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug

# Install to connected Samsung device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
