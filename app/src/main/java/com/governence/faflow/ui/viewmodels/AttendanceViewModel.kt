package com.governence.faflow.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.governence.faflow.domain.model.AttendanceStatus
import com.governence.faflow.domain.model.StaffAttendanceRecord
import com.governence.faflow.face.model.FaceDetectionResult
import com.governence.faflow.faflow.data.GeofenceRepository
import com.governence.faflow.location.CampusGeofence
import com.governence.faflow.location.LocationVerificationResult
import com.governence.faflow.location.StaffLiveLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Real-time face detection & positioning quality UI states for staff attendance.
 */
sealed interface FaceDetectionUiState {
    data object NoFace : FaceDetectionUiState
    data class FaceDetected(val count: Int, val primaryFace: FaceDetectionResult) : FaceDetectionUiState
    data class MultipleFaces(val count: Int) : FaceDetectionUiState
    data object FaceTooSmall : FaceDetectionUiState
    data object FaceTooLarge : FaceDetectionUiState
    data object FacePartiallyOutOfFrame : FaceDetectionUiState
    data object FaceOutsideGuide : FaceDetectionUiState
    data class FacePositionValid(val primaryFace: FaceDetectionResult) : FaceDetectionUiState
    data class DetectionError(val message: String) : FaceDetectionUiState
}

data class AttendanceUiState(
    val isCheckingIn: Boolean = true,
    val isShiftActive: Boolean = false,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val attendanceStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    val attendanceRecords: List<StaffAttendanceRecord> = emptyList(),
    val errorMessage: String? = null
)

class AttendanceViewModel(
    private val geofenceRepository: GeofenceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    private val _faceDetectionState = MutableStateFlow<FaceDetectionUiState>(FaceDetectionUiState.NoFace)
    val faceDetectionState: StateFlow<FaceDetectionUiState> = _faceDetectionState.asStateFlow()

    val verificationResult: StateFlow<LocationVerificationResult> = geofenceRepository.verificationResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationVerificationResult.Loading)

    val liveLocation: StateFlow<StaffLiveLocation?> = geofenceRepository.liveLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val geofences: StateFlow<List<CampusGeofence>> = geofenceRepository.geofences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Evaluates detected faces against the positioning guide oval and quality standards.
     */
    fun updateDetections(detections: List<FaceDetectionResult>, frameWidth: Int = 640, frameHeight: Int = 480) {
        _faceDetectionState.value = when {
            detections.isEmpty() -> FaceDetectionUiState.NoFace
            detections.size > 1 -> FaceDetectionUiState.MultipleFaces(detections.size)
            else -> {
                val face = detections.first()
                val box = face.boundingBox

                val isOutOfBounds = box.left < 10f || box.top < 10f || box.right > (frameWidth - 10f) || box.bottom > (frameHeight - 10f)
                val faceWidthRatio = box.width / frameWidth.toFloat()

                when {
                    face.confidence < 0.50f -> FaceDetectionUiState.NoFace
                    isOutOfBounds -> FaceDetectionUiState.FacePartiallyOutOfFrame
                    faceWidthRatio < 0.20f -> FaceDetectionUiState.FaceTooSmall
                    faceWidthRatio > 0.85f -> FaceDetectionUiState.FaceTooLarge
                    !face.quality.isFrontal -> FaceDetectionUiState.FaceDetected(count = 1, primaryFace = face)
                    else -> FaceDetectionUiState.FacePositionValid(primaryFace = face)
                }
            }
        }
    }

    fun refreshLocation() {
        geofenceRepository.startLocationMonitoring()
    }

    fun hasLocationPermission(): Boolean = geofenceRepository.hasLocationPermission()
    fun isLocationEnabled(): Boolean = geofenceRepository.isLocationEnabled()

    fun isLocationVerifiedForAttendance(): Boolean {
        return when (verificationResult.value) {
            is LocationVerificationResult.InsideGeofence -> true
            is LocationVerificationResult.Boundary -> true
            else -> false
        }
    }

    fun isFacePositionValid(): Boolean {
        return faceDetectionState.value is FaceDetectionUiState.FacePositionValid
    }

    fun performCheckIn(onSuccess: () -> Unit) {
        if (!isLocationVerifiedForAttendance()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Cannot check in: Staff member must be physically inside an active campus geofence boundary.")
            return
        }

        val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        _uiState.value = _uiState.value.copy(
            isCheckingIn = false,
            isShiftActive = true,
            checkInTime = now,
            errorMessage = null
        )
        onSuccess()
    }

    fun performCheckOut(onSuccess: () -> Unit) {
        if (!isLocationVerifiedForAttendance()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Cannot check out: Staff member must be physically inside an active campus geofence boundary.")
            return
        }

        val now = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        _uiState.value = _uiState.value.copy(
            isCheckingIn = true,
            isShiftActive = false,
            checkOutTime = now,
            errorMessage = null
        )
        onSuccess()
    }

    override fun onCleared() {
        super.onCleared()
        geofenceRepository.stopLocationMonitoring()
    }
}
