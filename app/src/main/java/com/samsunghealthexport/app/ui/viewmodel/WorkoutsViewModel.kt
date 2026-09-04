package com.samsunghealthexport.app.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samsunghealthexport.app.archive.SamsungHealthArchiveParser
import com.samsunghealthexport.app.healthconnect.HealthConnectManager
import com.samsunghealthexport.app.model.ExerciseType
import com.samsunghealthexport.app.model.WorkoutSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

data class WorkoutsUiState(
    val workouts: List<WorkoutSession> = emptyList(),
    val filteredWorkouts: List<WorkoutSession> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedWorkoutIds: Set<String> = emptySet(),
    val selectedSportFilter: ExerciseType? = null,
    val isHealthConnectAvailable: Boolean = false,
    val hasPermissions: Boolean = false
)

class WorkoutsViewModel(application: Application) : AndroidViewModel(application) {

    private val healthConnectManager = HealthConnectManager(application)

    private val _uiState = MutableStateFlow(WorkoutsUiState())
    val uiState: StateFlow<WorkoutsUiState> = _uiState.asStateFlow()

    init {
        checkHealthConnectStatus()
    }

    fun checkHealthConnectStatus() {
        val available = healthConnectManager.isAvailable()
        viewModelScope.launch {
            val hasPerms = if (available) healthConnectManager.hasAllPermissions() else false
            _uiState.value = _uiState.value.copy(
                isHealthConnectAvailable = available,
                hasPermissions = hasPerms
            )
            if (hasPerms) {
                loadHealthConnectWorkouts()
            }
        }
    }

    fun loadHealthConnectWorkouts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val sessions = healthConnectManager.fetchWorkouts()
                val currentList = _uiState.value.workouts.filter { it.source != com.samsunghealthexport.app.model.WorkoutSource.SAMSUNG_HEALTH_CONNECT }
                val merged = (sessions + currentList).distinctBy { it.id }.sortedByDescending { it.startTime }
                _uiState.value = _uiState.value.copy(
                    workouts = merged,
                    isLoading = false
                )
                applyFilter()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load Health Connect workouts: ${e.localizedMessage}"
                )
            }
        }
    }

    fun importArchiveZip(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
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
                        errorMessage = "No valid Samsung Health workout records found in this archive."
                    )
                } else {
                    val merged = (imported + _uiState.value.workouts).distinctBy { it.id }.sortedByDescending { it.startTime }
                    _uiState.value = _uiState.value.copy(
                        workouts = merged,
                        isLoading = false
                    )
                    applyFilter()
                }
            } catch (e: Exception) {
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
