package com.samsunghealthexport.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samsunghealthexport.app.archive.SamsungHealthArchiveParser
import com.samsunghealthexport.app.healthconnect.HealthConnectManager
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.WorkoutSession
import com.samsunghealthexport.app.model.WorkoutSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.Duration
import java.time.Instant

enum class TimeRangeFilterOption(val label: String, val durationDays: Long?) {
    DAYS_30("Last 30 Days", 30),
    DAYS_90("Last 90 Days", 90),
    PAST_YEAR("Past Year", 365),
    ALL_TIME("All Time", null)
}

data class WorkoutsUiState(
    val workouts: List<WorkoutSession> = emptyList(),
    val filteredWorkouts: List<WorkoutSession> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val selectedWorkoutIds: Set<String> = emptySet(),
    val selectedSportFilter: ExerciseType? = null,
    val selectedTimeRange: TimeRangeFilterOption = TimeRangeFilterOption.PAST_YEAR,
    val isHealthConnectAvailable: Boolean = false,
    val hasRequiredPermissions: Boolean = false,
    val hasHistoryPermission: Boolean = false,
    val healthConnectCount: Int = 0,
    val archiveCount: Int = 0
)

class WorkoutsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "WorkoutsViewModel"
    }

    private val healthConnectManager = HealthConnectManager(application)

    private val _uiState = MutableStateFlow(WorkoutsUiState())
    val uiState: StateFlow<WorkoutsUiState> = _uiState.asStateFlow()

    init {
        checkHealthConnectStatus()
    }

    fun checkHealthConnectStatus() {
        val available = healthConnectManager.isAvailable()
        viewModelScope.launch {
            val hasRequired = if (available) healthConnectManager.hasRequiredPermissions() else false
            val hasHistory = if (available) healthConnectManager.hasHistoryPermission() else false

            _uiState.value = _uiState.value.copy(
                isHealthConnectAvailable = available,
                hasRequiredPermissions = hasRequired,
                hasHistoryPermission = hasHistory
            )

            if (hasRequired) {
                loadHealthConnectWorkouts()
            }
        }
    }

    fun setTimeRangeFilter(option: TimeRangeFilterOption) {
        _uiState.value = _uiState.value.copy(selectedTimeRange = option)
        if (_uiState.value.hasRequiredPermissions) {
            loadHealthConnectWorkouts()
        }
    }

    fun loadHealthConnectWorkouts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, statusMessage = null)
            try {
                val timeRange = _uiState.value.selectedTimeRange
                val startTime = if (timeRange.durationDays != null) {
                    Instant.now().minus(Duration.ofDays(timeRange.durationDays))
                } else {
                    Instant.parse("2018-01-01T00:00:00Z") // All time
                }

                Log.i(TAG, "Fetching workouts from Health Connect from $startTime to now")
                val sessions = healthConnectManager.fetchWorkouts(requestedStartTime = startTime)

                val archiveList = _uiState.value.workouts.filter { it.source != WorkoutSource.SAMSUNG_HEALTH_CONNECT }
                val merged = (sessions + archiveList).distinctBy { it.id }.sortedByDescending { it.startTime }

                val hcCount = sessions.size
                val archCount = archiveList.size

                val status = if (hcCount == 0 && archCount == 0) {
                    "No workouts found in Health Connect. Note: Samsung Health only syncs workouts recorded after connecting to Health Connect. For older workouts, use the Import tab."
                } else {
                    "Found $hcCount workouts from Health Connect"
                }

                _uiState.value = _uiState.value.copy(
                    workouts = merged,
                    isLoading = false,
                    statusMessage = status,
                    healthConnectCount = hcCount,
                    archiveCount = archCount
                )
                applyFilter()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Health Connect workouts", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error querying Health Connect: ${e.localizedMessage}"
                )
            }
        }
    }

    fun importArchiveZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null, statusMessage = null)
            try {
                val context = getApplication<Application>()
                val stream: InputStream? = context.contentResolver.openInputStream(uri)
                if (stream == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Unable to read selected ZIP archive file."
                    )
                    return@launch
                }

                val imported = SamsungHealthArchiveParser.parseZip(stream)
                stream.close()

                if (imported.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No workout records found in this ZIP archive. Ensure it contains Samsung Health exercise CSV and JSON files."
                    )
                } else {
                    val currentList = _uiState.value.workouts.filter { it.source != WorkoutSource.SAMSUNG_HEALTH_ARCHIVE }
                    val merged = (imported + currentList).distinctBy { it.id }.sortedByDescending { it.startTime }

                    val archCount = imported.size
                    val hcCount = currentList.count { it.source == WorkoutSource.SAMSUNG_HEALTH_CONNECT }

                    _uiState.value = _uiState.value.copy(
                        workouts = merged,
                        isLoading = false,
                        statusMessage = "Successfully imported $archCount workouts from Samsung Health archive!",
                        archiveCount = archCount,
                        healthConnectCount = hcCount
                    )
                    applyFilter()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error importing archive", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error importing archive: ${e.localizedMessage}"
                )
            }
        }
    }

    fun addLiveSession(session: WorkoutSession) {
        val merged = (listOf(session) + _uiState.value.workouts).distinctBy { it.id }.sortedByDescending { it.startTime }
        _uiState.value = _uiState.value.copy(workouts = merged)
        applyFilter()
    }

    fun setSportFilter(filter: ExerciseType?) {
        _uiState.value = _uiState.value.copy(selectedSportFilter = filter)
        applyFilter()
    }

    fun toggleWorkoutSelection(id: String) {
        val current = _uiState.value.selectedWorkoutIds.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _uiState.value = _uiState.value.copy(selectedWorkoutIds = current)
    }

    fun selectAll() {
        val allIds = _uiState.value.filteredWorkouts.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(selectedWorkoutIds = allIds)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(selectedWorkoutIds = emptySet())
    }

    private fun applyFilter() {
        val filter = _uiState.value.selectedSportFilter
        val list = _uiState.value.workouts
        val filtered = if (filter == null) {
            list
        } else {
            list.filter { it.exerciseType == filter }
        }
        _uiState.value = _uiState.value.copy(filteredWorkouts = filtered)
    }
}
