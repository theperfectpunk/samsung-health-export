package com.samsunghealthexport.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.ExportFormat
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.ui.components.WorkoutCard
import com.samsunghealthexport.app.ui.theme.DarkBackground
import com.samsunghealthexport.app.ui.theme.DarkCard
import com.samsunghealthexport.app.ui.theme.OrangeFlame
import com.samsunghealthexport.app.ui.theme.SamsungBlue
import com.samsunghealthexport.app.ui.viewmodel.WorkoutsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsListScreen(
    viewModel: WorkoutsViewModel,
    onRequestHealthConnectPermissions: () -> Unit,
    onWorkoutClick: (WorkoutSession) -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedSessionForExport by remember { mutableStateOf<WorkoutSession?>(null) }
    var isExportMultiOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Workouts",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${state.filteredWorkouts.size} workouts extracted",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.loadHealthConnectWorkouts() },
                    modifier = Modifier
                        .size(42.dp)
                        .background(DarkCard, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync Health Connect",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (state.selectedWorkoutIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { isExportMultiOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export (${state.selectedWorkoutIds.size})")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Health Connect Permissions Banner if not connected
        if (!state.hasPermissions) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrangeFlame.copy(alpha = 0.15f))
                    .clickable { onRequestHealthConnectPermissions() }
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = OrangeFlame,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Connect Samsung Health",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            color = OrangeFlame
                        )
                        Text(
                            text = "Grant Health Connect permissions to extract GPS routes, heart rate, and workouts.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Sport filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = state.selectedSportFilter == null,
                    onClick = { viewModel.setSportFilter(null) },
                    label = { Text("All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SamsungBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
            items(listOf(ExerciseType.RUNNING, ExerciseType.CYCLING, ExerciseType.WALKING, ExerciseType.HIKING)) { sport ->
                FilterChip(
                    selected = state.selectedSportFilter == sport,
                    onClick = { viewModel.setSportFilter(if (state.selectedSportFilter == sport) null else sport) },
                    label = { Text(sport.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SamsungBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Workouts List or Loading
        if (state.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SamsungBlue)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Extracting Samsung Health workout data...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (state.filteredWorkouts.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "No Workouts Found",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ensure Samsung Health is synced with Health Connect, or import a personal data ZIP archive in the Import tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(state.filteredWorkouts, key = { it.id }) { session ->
                    WorkoutCard(
                        session = session,
                        isSelected = state.selectedWorkoutIds.contains(session.id),
                        onSelectToggle = { viewModel.toggleWorkoutSelection(session.id) },
                        onClick = { onWorkoutClick(session) },
                        onQuickExport = { selectedSessionForExport = session }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Export Single Workout BottomSheet Modal
    if (selectedSessionForExport != null) {
        ExportBottomSheet(
            session = selectedSessionForExport!!,
            context = context,
            onDismiss = { selectedSessionForExport = null }
        )
    }

    // Export Multiple Workouts Dialog
    if (isExportMultiOpen) {
        val selectedList = state.filteredWorkouts.filter { state.selectedWorkoutIds.contains(it.id) }
        MultiExportBottomSheet(
            sessions = selectedList,
            context = context,
            onDismiss = { isExportMultiOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportBottomSheet(
    session: WorkoutSession,
    context: Context,
    onDismiss: () -> Unit
) {
    var selectedFormat by remember { mutableStateOf(ExportFormat.COMBINED_CSV) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkCard
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = "Export Workout Data",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            ExportFormat.entries.forEach { format ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedFormat = format }
                        .padding(vertical = 10.dp)
                ) {
                    RadioButton(
                        selected = selectedFormat == format,
                        onClick = { selectedFormat = format }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = format.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = format.description,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val (_, uri) = ExportFileManager.writeToCacheAndGetUri(context, session, selectedFormat)
                        val intent = ExportFileManager.createShareIntent(uri, "${session.title}.${selectedFormat.extension}")
                        context.startActivity(intent)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share / Open CSV")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiExportBottomSheet(
    sessions: List<WorkoutSession>,
    context: Context,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkCard
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = "Export ${sessions.size} Workouts",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val (_, uri) = ExportFileManager.writeMultiSummaryToCache(context, sessions)
                    val intent = ExportFileManager.createShareIntent(uri, "samsung_health_summary.csv")
                    context.startActivity(intent)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export Multi-Workout Summary CSV")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
