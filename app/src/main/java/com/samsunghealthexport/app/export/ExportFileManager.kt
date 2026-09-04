package com.samsunghealthexport.app.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.samsunghealthexport.app.model.ExportFormat
import com.samsunghealthexport.app.model.WorkoutSession
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter

object ExportFileManager {

    private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    /**
     * Writes exported workout data to application cache and returns FileProvider Uri for sharing.
     */
    fun writeToCacheAndGetUri(
        context: Context,
        session: WorkoutSession,
        format: ExportFormat,
        delimiter: String = ","
    ): Pair<File, Uri> {
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val sanitizedTitle = session.title.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val dateStr = session.startTime.atZone(java.time.ZoneId.systemDefault()).format(FILE_DATE_FORMAT)
        val filename = "${sanitizedTitle}_${dateStr}.${format.extension}"
        val file = File(cacheDir, filename)

        val content = CsvExporter.exportWorkout(session, format, delimiter)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(file, uri)
    }

    /**
     * Exports multi-workout summary CSV to cache.
     */
    fun writeMultiSummaryToCache(
        context: Context,
        sessions: List<WorkoutSession>,
        delimiter: String = ","
    ): Pair<File, Uri> {
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val filename = "samsung_health_summary_${System.currentTimeMillis()}.csv"
        val file = File(cacheDir, filename)

        val content = CsvExporter.exportMultiSummary(sessions, delimiter)
        file.writeText(content)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(file, uri)
    }

    /**
     * Saves exported file to the device's public Downloads directory.
     */
    fun saveToDownloads(
        context: Context,
        filename: String,
        content: String,
        mimeType: String = "text/csv"
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SamsungHealthExports")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return false
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray(Charsets.UTF_8))
                }
                true
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val targetDir = File(downloadsDir, "SamsungHealthExports").apply { mkdirs() }
                val targetFile = File(targetDir, filename)
                targetFile.writeText(content)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Creates an Android Share Sheet Intent for the exported file.
     */
    fun createShareIntent(uri: Uri, filename: String, mimeType: String = "text/csv"): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
    }
}
