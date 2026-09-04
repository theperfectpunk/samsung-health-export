package com.samsunghealthexport.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.samsunghealthexport.app.healthconnect.HealthConnectPermissions
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.ui.screens.ArchiveImportScreen
import com.samsunghealthexport.app.ui.screens.LiveWorkoutScreen
import com.samsunghealthexport.app.ui.screens.SettingsScreen
import com.samsunghealthexport.app.ui.screens.WorkoutDetailScreen
import com.samsunghealthexport.app.ui.screens.WorkoutsListScreen
import com.samsunghealthexport.app.ui.theme.DarkBackground
import com.samsunghealthexport.app.ui.theme.DarkSurface
import com.samsunghealthexport.app.ui.theme.SamsungBlue
import com.samsunghealthexport.app.ui.theme.SamsungHealthExportTheme
import com.samsunghealthexport.app.ui.viewmodel.LiveWorkoutViewModel
import com.samsunghealthexport.app.ui.viewmodel.WorkoutsViewModel

class MainActivity : ComponentActivity() {

    private val workoutsViewModel: WorkoutsViewModel by viewModels()
    private val liveWorkoutViewModel: LiveWorkoutViewModel by viewModels()

    // Health Connect Permission Launcher
    private val healthConnectPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        workoutsViewModel.checkHealthConnectStatus()
    }

    // Android System Permissions Launcher (Location, Bluetooth, Notifications)
    private val systemPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestRequiredSystemPermissions()

        setContent {
            SamsungHealthExportTheme {
                MainApp(
                    workoutsViewModel = workoutsViewModel,
                    liveWorkoutViewModel = liveWorkoutViewModel,
                    onRequestHealthConnectPermissions = {
                        healthConnectPermissionLauncher.launch(HealthConnectPermissions.PERMISSIONS)
                    }
                )
            }
        }
    }

    private fun requestRequiredSystemPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            systemPermissionsLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
fun MainApp(
    workoutsViewModel: WorkoutsViewModel,
    liveWorkoutViewModel: LiveWorkoutViewModel,
    onRequestHealthConnectPermissions: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedWorkoutDetail by remember { mutableStateOf<WorkoutSession?>(null) }

    Scaffold(
        bottomBar = {
            if (selectedWorkoutDetail == null) {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.DirectionsRun, contentDescription = "Workouts") },
                        label = { Text("Workouts") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = SamsungBlue,
                            indicatorColor = SamsungBlue
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = "Live") },
                        label = { Text("Live Tracker") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = SamsungBlue,
                            indicatorColor = SamsungBlue
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Archive, contentDescription = "Import") },
                        label = { Text("Import") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = SamsungBlue,
                            indicatorColor = SamsungBlue
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = SamsungBlue,
                            indicatorColor = SamsungBlue
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(innerPadding)
        ) {
            if (selectedWorkoutDetail != null) {
                WorkoutDetailScreen(
                    session = selectedWorkoutDetail!!,
                    onBack = { selectedWorkoutDetail = null }
                )
            } else {
                when (selectedTab) {
                    0 -> WorkoutsListScreen(
                        viewModel = workoutsViewModel,
                        onRequestHealthConnectPermissions = onRequestHealthConnectPermissions,
                        onWorkoutClick = { session -> selectedWorkoutDetail = session }
                    )
                    1 -> LiveWorkoutScreen(
                        viewModel = liveWorkoutViewModel,
                        onWorkoutFinished = { session ->
                            workoutsViewModel.addLiveSession(session)
                            selectedWorkoutDetail = session
                        }
                    )
                    2 -> ArchiveImportScreen(
                        viewModel = workoutsViewModel,
                        onNavigateToWorkouts = { selectedTab = 0 }
                    )
                    3 -> SettingsScreen(
                        viewModel = workoutsViewModel,
                        onRequestHealthConnectPermissions = onRequestHealthConnectPermissions
                    )
                }
            }
        }
    }
}
