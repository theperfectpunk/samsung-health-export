package com.samsunghealthexport.app.export

import com.samsunghealthexport.app.model.WorkoutSession
import java.time.format.DateTimeFormatter
import java.util.Locale

object GpxExporter {

    private val ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT

    /**
     * Builds a GPX 1.1 XML string containing route coordinates, elevation, timestamp,
     * and Garmin/Strava trackpoint extensions for Heart Rate, Cadence, and Speed.
     */
    fun buildGpx(session: WorkoutSession): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Samsung Health Exporter\"\n")
        sb.append("     xmlns=\"http://www.topografix.com/GPX/1/1\"\n")
        sb.append("     xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
        sb.append("     xmlns:gpxtpx=\"http://www.garmin.com/xmlschemas/TrackPointExtension/v1\"\n")
        sb.append("     xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\n")
        sb.append("                         http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd\">\n")

        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(session.title)}</name>\n")
        sb.append("    <time>${ISO_FORMATTER.format(session.startTime)}</time>\n")
        sb.append("  </metadata>\n")

        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(session.title)}</name>\n")
        sb.append("    <type>${session.exerciseType.displayName.uppercase(Locale.US)}</type>\n")
        sb.append("    <trkseg>\n")

        // Prefer liveDataPoints with coordinates
        val pointsWithGps = session.liveDataPoints.filter { it.latitude != null && it.longitude != null }

        if (pointsWithGps.isNotEmpty()) {
            pointsWithGps.forEach { pt ->
                sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
                if (pt.altitudeMeters != null) {
                    sb.append("        <ele>${String.format(Locale.US, "%.1f", pt.altitudeMeters)}</ele>\n")
                }
                sb.append("        <time>${ISO_FORMATTER.format(pt.timestamp)}</time>\n")

                if (pt.heartRateBpm != null || pt.speedMps != null || pt.cadenceSpm != null) {
                    sb.append("        <extensions>\n")
                    sb.append("          <gpxtpx:TrackPointExtension>\n")
                    if (pt.heartRateBpm != null) {
                        sb.append("            <gpxtpx:hr>${pt.heartRateBpm}</gpxtpx:hr>\n")
                    }
                    if (pt.cadenceSpm != null) {
                        sb.append("            <gpxtpx:cad>${pt.cadenceSpm}</gpxtpx:cad>\n")
                    }
                    if (pt.speedMps != null) {
                        sb.append("            <gpxtpx:speed>${String.format(Locale.US, "%.2f", pt.speedMps)}</gpxtpx:speed>\n")
                    }
                    sb.append("          </gpxtpx:TrackPointExtension>\n")
                    sb.append("        </extensions>\n")
                }
                sb.append("      </trkpt>\n")
            }
        } else if (session.routePoints.isNotEmpty()) {
            session.routePoints.forEach { pt ->
                sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
                if (pt.altitudeMeters != null) {
                    sb.append("        <ele>${String.format(Locale.US, "%.1f", pt.altitudeMeters)}</ele>\n")
                }
                if (pt.timestamp != null) {
                    sb.append("        <time>${ISO_FORMATTER.format(pt.timestamp)}</time>\n")
                }
                sb.append("      </trkpt>\n")
            }
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
