package com.samsunghealthexport.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsunghealthexport.app.export.ExportFileManager
import com.samsunghealthexport.app.model.ExportFormat
import com.samsunghealthexport.app.model.WorkoutDataPoint
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.ui.components.MetricStatCard
import com.samsunghealthexport.app.ui.theme.BrightGreen
import com.samsunghealthexport.app.ui.theme.DarkBackground
import com.samsunghealthexport.app.ui.theme.DarkCard
import com.samsunghealthexport.app.ui.theme.HeartRed
import com.samsunghealthexport.app.ui.theme.OrangeFlame
import com.samsunghealthexport.app.ui.theme.SamsungBlue
import com.samsunghealthexport.app.util.Formatters

@Composable
fun WorkoutDetailScreen(
    session: WorkoutSession,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val s = session.summary
    var showExportSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Navigation Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(DarkCard, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${session.exerciseType.displayName} • ${Formatters.formatDateTime(session.startTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { showExportSheet = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(SamsungBlue, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Export",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // High-Level Summary Cards Row 1: Distance & Duration
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricStatCard(
                        title = "Distance",
                        value = "%.2f".format(s.totalDistanceKm),
                        unit = "km",
                        icon = Icons.Default.DirectionsRun,
                        accentColor = BrightGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Duration",
                        value = s.formattedDuration,
                        unit = "",
                        icon = Icons.Default.Timer,
                        accentColor = SamsungBlue,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // High-Level Summary Cards Row 2: Avg Pace & Avg HR
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MetricStatCard(
                        title = "Avg Pace",
                        value = s.formattedAvgPace,
                        unit = "",
                        icon = Icons.Default.Speed,
                        accentColor = OrangeFlame,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = "Avg Heart Rate",
                        value = s.avgHeartRateBpm?.let { "${it.toInt()}" } ?: "--",
                        unit = "bpm",
                        icon = Icons.Default.Favorite,
                        accentColor = HeartRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Detailed Statistics Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkCard)
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "WORKOUT AVERAGES & METRICS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        DetailRow("Sport", session.exerciseType.displayName)
                        DetailRow("Source", session.source.label)
                        DetailRow("Total Calories", s.totalCaloriesKcal?.let { "%.0f kcal".format(it) } ?: "--")
                        DetailRow("Average Speed", s.avgSpeedKmh?.let { "%.1f km/h".format(it) } ?: "--")
                        DetailRow("Max Speed", s.maxSpeedKmh?.let { "%.1f km/h".format(it) } ?: "--")
                        DetailRow("Best Pace", s.formattedBestPace)
                        DetailRow("Max Heart Rate", s.maxHeartRateBpm?.let { "$it bpm" } ?: "--")
                        DetailRow("Min Heart Rate", s.minHeartRateBpm?.let { "$it bpm" } ?: "--")
                        DetailRow("Elevation Gain", s.elevationGainMeters?.let { "%.1f m".format(it) } ?: "--")
                        DetailRow("Total Steps", s.totalSteps?.toString() ?: "--")
                        DetailRow("Avg Cadence", s.avgCadenceSpm?.let { "$it spm" } ?: "--")
                        DetailRow("Live Trackpoints", "${session.liveDataPoints.size} logged")
                    }
                }
            }

            // Live Time-Series Telemetry Preview
            item {
                Text(
                    text = "Live Track Data (${session.liveDataPoints.size} points)",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (session.liveDataPoints.isEmpty()) {
                item {
                    Text(
                        text = "No granular live data points found for this workout.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                itemsIndexed(session.liveDataPoints.take(50)) { index, pt ->
                    LivePointRow(index + 1, pt)
                }
                if (session.liveDataPoints.size > 50) {
                    item {
                        Text(
                            text = "... and ${session.liveDataPoints.size - 50} more points in full CSV export.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // Export Bottom Bar
        Button(
            onClick = { showExportSheet = true },
            colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(52.dp)
        ) {
            Icon(imageVector = Icons.Default.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export CSV / GPX", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    if (showExportSheet) {
        ExportBottomSheet(
            session = session,
            context = context,
            onDismiss = { showExportSheet = false }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LivePointRow(index: Int, pt: WorkoutDataPoint) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkCard.copy(alpha = 0.6f))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "+%ds".format(pt.elapsedSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(45.dp)
            )

            // Speed & Pace
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pt.speedKmh?.let { "%.1f km/h".format(it) } ?: "--",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pt.paceFormatted ?: "--",
                    style = MaterialTheme.typography.labelSmall,
                    color = OrangeFlame
                )
            }

            // Heart Rate
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = HeartRed,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = pt.heartRateBpm?.let { "$it bpm" } ?: "--",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Coordinates
            if (pt.latitude != null && pt.longitude != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "%.4f, %.4f".format(pt.latitude, pt.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = BrightGreen
                )
            }
        }
    }
}
