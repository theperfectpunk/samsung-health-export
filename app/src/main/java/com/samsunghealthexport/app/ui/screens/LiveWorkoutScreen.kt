package com.samsunghealthexport.app.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.samsunghealthexport.app.live.BleHeartRateManager
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.ExportFormat
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.ui.components.LiveMetricGauge
import com.samsunghealthexport.app.ui.components.MetricStatCard
import com.samsunghealthexport.app.ui.theme.BrightGreen
import com.samsunghealthexport.app.ui.theme.DarkBackground
import com.samsunghealthexport.app.ui.theme.DarkCard
import com.samsunghealthexport.app.ui.theme.HeartRed
import com.samsunghealthexport.app.ui.theme.OrangeFlame
import com.samsunghealthexport.app.ui.theme.SamsungBlue
import com.samsunghealthexport.app.ui.viewmodel.LiveWorkoutViewModel
import com.samsunghealthexport.app.util.Formatters

@Composable
fun LiveWorkoutScreen(
    viewModel: LiveWorkoutViewModel,
    onWorkoutFinished: (WorkoutSession) -> Unit
) {
    val liveState by viewModel.liveState.collectAsState()
    val bleState by viewModel.bleState.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val context = LocalContext.current

    var selectedSport by remember { mutableStateOf(ExerciseType.RUNNING) }
    var finishedSession by remember { mutableStateOf<WorkoutSession?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Screen Title & BLE Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Live Workout",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (liveState.isTracking) "Recording & Streaming to CSV" else "Ready to track",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (liveState.isTracking) BrightGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // BLE HR Sensor status badge
            BleSensorBadge(
                bleState = bleState,
                deviceName = connectedDevice,
                onClick = {
                    if (bleState == BleHeartRateManager.ConnectionState.CONNECTED) {
                        viewModel.disconnectBle()
                    } else {
                        viewModel.startBleScan()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!liveState.isTracking) {
            // Sport Selector before starting
            Text(
                text = "SELECT ACTIVITY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(ExerciseType.RUNNING, ExerciseType.CYCLING, ExerciseType.WALKING).forEach { sport ->
                    val isSelected = selectedSport == sport
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SamsungBlue else DarkCard)
                            .clickable { selectedSport = sport }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = sport.displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Ready card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkCard)
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = SamsungBlue,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "GPS & Live Telemetry Stream",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Real-time 1-second GPS points, instantaneous pace, speed, and BLE heart rate will be recorded and continuously streamed to CSV.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Start Button
            Button(
                onClick = { viewModel.startWorkout(selectedSport) },
                colors = ButtonDefaults.buttonColors(containerColor = BrightGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "START ${selectedSport.displayName.uppercase()}",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color.Black
                )
            }
        } else {
            // Live Active Workout Dashboard

            // Elapsed Duration Banner
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .padding(vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = SamsungBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = Formatters.formatDuration(liveState.elapsedSeconds),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 1.sp
                    )
                    if (liveState.isPaused) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(PAUSED)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = OrangeFlame
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Pace Gauge with Average Pace
            LiveMetricGauge(
                title = "Current Pace",
                value = liveState.currentPaceFormatted,
                unit = "",
                averageLabel = "AVERAGE PACE",
                averageValue = liveState.avgPaceFormatted,
                accentColor = OrangeFlame,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Live Speed & Live Heart Rate Gauges
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                LiveMetricGauge(
                    title = "Speed",
                    value = "%.1f".format(liveState.currentSpeedKmh),
                    unit = "km/h",
                    averageLabel = "AVG",
                    averageValue = liveState.avgSpeedKmh?.let { "%.1f".format(it) } ?: "--",
                    accentColor = SamsungBlue,
                    modifier = Modifier.weight(1f)
                )

                LiveMetricGauge(
                    title = "Heart Rate",
                    value = liveState.currentHeartRateBpm?.toString() ?: "--",
                    unit = "bpm",
                    averageLabel = "AVG",
                    averageValue = liveState.avgHeartRateBpm?.let { "${it.toInt()}" } ?: "--",
                    accentColor = HeartRed,
                    isHeartRate = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Distance & Calories Cards
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricStatCard(
                    title = "Distance",
                    value = "%.2f".format(liveState.totalDistanceMeters / 1000.0),
                    unit = "km",
                    icon = Icons.Default.DirectionsRun,
                    accentColor = BrightGreen,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Calories",
                    value = "%.0f".format(liveState.caloriesKcal),
                    unit = "kcal",
                    accentColor = OrangeFlame,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live CSV Streaming Status Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrightGreen.copy(alpha = 0.12f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = BrightGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Live CSV Auto-Export Active",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = BrightGreen
                        )
                        Text(
                            text = "${liveState.pointsCount} points written to disk in real-time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons: Pause/Resume & Finish
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!liveState.isPaused) {
                    OutlinedButton(
                        onClick = { viewModel.pauseWorkout() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pause")
                    }
                } else {
                    Button(
                        onClick = { viewModel.resumeWorkout() },
                        colors = ButtonDefaults.buttonColors(containerColor = BrightGreen),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resume", color = Color.Black)
                    }
                }

                Button(
                    onClick = {
                        val session = viewModel.finishWorkout()
                        if (session != null) {
                            finishedSession = session
                            onWorkoutFinished(session)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HeartRed),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Finish & Export")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Workout Finished Modal
    if (finishedSession != null) {
        ExportBottomSheet(
            session = finishedSession!!,
            context = context,
            onDismiss = { finishedSession = null }
        )
    }
}

@Composable
private fun BleSensorBadge(
    bleState: BleHeartRateManager.ConnectionState,
    deviceName: String?,
    onClick: () -> Unit
) {
    val (color, label) = when (bleState) {
        BleHeartRateManager.ConnectionState.CONNECTED -> Pair(BrightGreen, deviceName ?: "HR Connected")
        BleHeartRateManager.ConnectionState.CONNECTING -> Pair(OrangeFlame, "Connecting...")
        BleHeartRateManager.ConnectionState.SCANNING -> Pair(SamsungBlue, "Scanning HR...")
        BleHeartRateManager.ConnectionState.DISCONNECTED -> Pair(MaterialTheme.colorScheme.onSurfaceVariant, "Connect HR")
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (bleState == BleHeartRateManager.ConnectionState.CONNECTED)
                Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
