package com.samsunghealthexport.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.samsunghealthexport.app.model.WorkoutSource
import com.samsunghealthexport.app.ui.theme.BrightGreen
import com.samsunghealthexport.app.ui.theme.DarkBackground
import com.samsunghealthexport.app.ui.theme.DarkCard
import com.samsunghealthexport.app.ui.theme.OrangeFlame
import com.samsunghealthexport.app.ui.theme.SamsungBlue
import com.samsunghealthexport.app.ui.viewmodel.WorkoutsViewModel

@Composable
fun ArchiveImportScreen(
    viewModel: WorkoutsViewModel,
    onNavigateToWorkouts: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importArchiveZip(uri)
        }
    }

    val importedCount = state.workouts.count { it.source == WorkoutSource.SAMSUNG_HEALTH_ARCHIVE }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Samsung Health Archive",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Import offline backup ZIP files directly from Samsung Health",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // How to export from Samsung Health Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkCard)
                .padding(20.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = SamsungBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HOW TO GET YOUR DATA FROM SAMSUNG HEALTH",
                        style = MaterialTheme.typography.labelSmall,
                        color = SamsungBlue,
                        letterSpacing = 0.8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                InstructionStep(1, "Open Samsung Health on your Galaxy smartphone.")
                InstructionStep(2, "Tap the Settings icon (Gear in top right or menu).")
                InstructionStep(3, "Scroll down and tap 'Download personal data'.")
                InstructionStep(4, "Request your archive. Samsung Health will generate a ZIP file containing exercise summaries, live_data JSON, and location_data JSON.")
                InstructionStep(5, "Once downloaded, tap the button below to import the ZIP.")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Import Button
        Button(
            onClick = {
                filePicker.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
            },
            colors = ButtonDefaults.buttonColors(containerColor = SamsungBlue),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(imageVector = Icons.Default.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "SELECT SAMSUNG HEALTH ZIP ARCHIVE",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Loading or Result State
        if (state.isLoading) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(DarkCard)
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = SamsungBlue)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Parsing ZIP archive, exercise CSV, and live data JSONs...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else if (state.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OrangeFlame.copy(alpha = 0.15f))
                    .padding(16.dp)
            ) {
                Text(
                    text = state.errorMessage!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OrangeFlame
                )
            }
        } else if (importedCount > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrightGreen.copy(alpha = 0.12f))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = BrightGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$importedCount Archive Workouts Loaded",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                            fontWeight = FontWeight.Bold,
                            color = BrightGreen
                        )
                        Text(
                            text = "Live GPS coordinates, heart rate series, and averages are ready for CSV export.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onNavigateToWorkouts,
                            colors = ButtonDefaults.buttonColors(containerColor = BrightGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("View & Export Workouts", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InstructionStep(step: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = SamsungBlue
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp
        )
    }
}
