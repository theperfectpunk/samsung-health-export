package com.samsunghealthexport.app.model

enum class ExportFormat(val displayName: String, val extension: String, val description: String) {
    COMBINED_CSV(
        displayName = "Combined CSV (Summary + Live Track)",
        extension = "csv",
        description = "Header section with workout averages & statistics followed by full time-series GPS, HR, speed, and pace."
    ),
    LIVE_DATA_ONLY_CSV(
        displayName = "Live Data Only CSV (Raw Time-Series)",
        extension = "csv",
        description = "Pure tabular data with one row per timestamp, suitable for Python, Pandas, and Excel."
    ),
    SUMMARY_ONLY_CSV(
        displayName = "Workout Summary CSV",
        extension = "csv",
        description = "Single row with session averages, total distance, duration, calories, and peak metrics."
    ),
    GPX(
        displayName = "GPX with Trackpoint Extensions",
        extension = "gpx",
        description = "Standard GPS route with HR and speed extensions compatible with Strava and Garmin."
    )
}
