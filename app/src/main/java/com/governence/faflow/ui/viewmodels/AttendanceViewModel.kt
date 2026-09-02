package com.governence.faflow.ui.viewmodels

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.governence.faflow.domain.model.AttendanceStatus
import com.governence.faflow.domain.model.StaffAttendanceRecord
import com.governence.faflow.face.liveness.BiometricVerificationResult
import com.governence.faflow.face.liveness.LivenessState
import com.governence.faflow.face.liveness.PresentationAttackRisk
import com.governence.faflow.face.model.FaceDetectionResult
import com.governence.faflow.face.model.StaffBiometricVerificationState
import com.governence.faflow.face.recognition.FaceRecognitionEngine
import com.governence.faflow.faflow.data.GeofenceRepository
import com.governence.faflow.location.CampusGeofence
import com.governence.faflow.location.LocationVerificationResult
import com.governence.faflow.location.StaffLiveLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

/**
 * Overall attendance gating state combining location, face, identity, and liveness.
 */
sealed interface AttendanceEligibilityState {
    data object CheckingRequirements : AttendanceEligibilityState
    data object LocationRequired : AttendanceEligibilityState
    data object FaceRequired : AttendanceEligibilityState
    data object SingleFaceRequired : AttendanceEligibilityState
    data object IdentityVerificationRequired : AttendanceEligibilityState
    data object LivenessRequired : AttendanceEligibilityState
    data class VerifiedAndReady(val staffId: String, val similarity: Float, val livenessScore: Float) : AttendanceEligibilityState
    data class Blocked(val reason: String) : AttendanceEligibilityState
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
    private val geofenceRepository: GeofenceRepository,
    private val recognitionEngine: FaceRecognitionEngine? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    private val _faceDetectionState = MutableStateFlow<FaceDetectionUiState>(FaceDetectionUiState.NoFace)
    val faceDetectionState: StateFlow<FaceDetectionUiState> = _faceDetectionState.asStateFlow()

    private val _identityVerificationState = MutableStateFlow<StaffBiometricVerificationState>(StaffBiometricVerificationState.NoFace)
    val identityVerificationState: StateFlow<StaffBiometricVerificationState> = _identityVerificationState.asStateFlow()

    private val _livenessState = MutableStateFlow<LivenessState>(LivenessState.WaitingForFace)
    val livenessState: StateFlow<LivenessState> = _livenessState.asStateFlow()

    private val _biometricVerificationState = MutableStateFlow(
        BiometricVerificationResult(
            staffId = "",
            identityVerified = false,
            similarityScore = 0f,
            livenessVerified = false,
            livenessScore = 0f,
            presentationAttackRisk = PresentationAttackRisk.LOW
        )
    )
    val biometricVerificationState: StateFlow<BiometricVerificationResult> = _biometricVerificationState.asStateFlow()

    val verificationResult: StateFlow<LocationVerificationResult> = geofenceRepository.verificationResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LocationVerificationResult.Loading)

    val liveLocation: StateFlow<StaffLiveLocation?> = geofenceRepository.liveLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val geofences: StateFlow<List<CampusGeofence>> = geofenceRepository.geofences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Combined attendance eligibility state machine.
     */
    val attendanceEligibilityState: StateFlow<AttendanceEligibilityState> = combine(
        verificationResult,
        faceDetectionState,
        identityVerificationState,
        livenessState
    ) { loc, face, identity, liveness ->
        val isLocValid = when (loc) {
            is LocationVerificationResult.InsideGeofence, is LocationVerificationResult.Boundary -> true
            else -> false
        }

        when {
            !isLocValid -> {
                when (loc) {
                    is LocationVerificationResult.MockLocationDetected -> AttendanceEligibilityState.Blocked("Fake GPS Spoofing Detected")
                    is LocationVerificationResult.AccuracyInsufficient -> AttendanceEligibilityState.Blocked("GPS accuracy insufficient (±${loc.currentAccuracyMeters.toInt()}m)")
                    else -> AttendanceEligibilityState.LocationRequired
                }
            }
            face is FaceDetectionUiState.MultipleFaces -> AttendanceEligibilityState.SingleFaceRequired
            face !is FaceDetectionUiState.FacePositionValid && face !is FaceDetectionUiState.FaceDetected -> AttendanceEligibilityState.FaceRequired
            identity is StaffBiometricVerificationState.VerificationFailed -> AttendanceEligibilityState.Blocked(identity.reason)
            identity is StaffBiometricVerificationState.NoEnrollment -> AttendanceEligibilityState.Blocked("Biometric profile not enrolled for staff member #${identity.staffId}")
            identity !is StaffBiometricVerificationState.Verified -> AttendanceEligibilityState.IdentityVerificationRequired
            liveness is LivenessState.SpoofSuspected -> AttendanceEligibilityState.Blocked("Presentation attack suspected: ${liveness.reason}")
            liveness is LivenessState.TimedOut -> AttendanceEligibilityState.Blocked("Liveness challenge timed out. Please try again.")
            liveness !is LivenessState.Passed -> AttendanceEligibilityState.LivenessRequired
            else -> AttendanceEligibilityState.VerifiedAndReady(
                staffId = (identity as StaffBiometricVerificationState.Verified).staffId,
                similarity = identity.similarity,
                livenessScore = (liveness as LivenessState.Passed).livenessScore
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AttendanceEligibilityState.CheckingRequirements)

    /**
     * Evaluates detected faces against the positioning guide oval, identity recognition, and liveness.
     */
    fun updateDetections(
        detections: List<FaceDetectionResult>,
        sourceBitmap: Bitmap? = null,
        staffId: String? = null,
        frameWidth: Int = 640,
        frameHeight: Int = 480
    ) {
        val newState = when {
            detections.isEmpty() -> {
                _identityVerificationState.value = StaffBiometricVerificationState.NoFace
                _livenessState.value = LivenessState.WaitingForFace
                recognitionEngine?.livenessEngine?.reset()
                FaceDetectionUiState.NoFace
            }
            detections.size > 1 -> {
                _identityVerificationState.value = StaffBiometricVerificationState.MultipleFaces(detections.size)
                _livenessState.value = LivenessState.FaceNotSuitable("Multiple faces visible")
                recognitionEngine?.livenessEngine?.reset()
                FaceDetectionUiState.MultipleFaces(detections.size)
            }
            else -> {
                val face = detections.first()
                val box = face.boundingBox

                val isOutOfBounds = box.left < 10f || box.top < 10f || box.right > (frameWidth - 10f) || box.bottom > (frameHeight - 10f)
                val faceWidthRatio = box.width / frameWidth.toFloat()

                when {
                    face.confidence < 0.50f -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.NoFace
                        FaceDetectionUiState.NoFace
                    }
                    isOutOfBounds -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.FaceOutOfFrame
                        FaceDetectionUiState.FacePartiallyOutOfFrame
                    }
                    faceWidthRatio < 0.20f -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.FaceTooSmall
                        FaceDetectionUiState.FaceTooSmall
                    }
                    faceWidthRatio > 0.85f -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.FaceTooLarge
                        FaceDetectionUiState.FaceTooLarge
                    }
                    else -> {
                        // 1. Evaluate Identity Match
                        if (sourceBitmap != null && staffId != null && recognitionEngine != null && _identityVerificationState.value !is StaffBiometricVerificationState.Verified) {
                            runRecognition(sourceBitmap, face, staffId)
                        }

                        // 2. Evaluate Live Liveness Pipeline
                        if (recognitionEngine != null) {
                            val liveState = recognitionEngine.livenessEngine.processFrame(face)
                            _livenessState.value = liveState

                            if (staffId != null) {
                                _biometricVerificationState.value = recognitionEngine.evaluateBiometricAuthorization(
                                    staffId = staffId,
                                    identityState = _identityVerificationState.value,
                                    livenessState = liveState
                                )
                            }
                        }

                        if (!face.quality.isFrontal) {
                            FaceDetectionUiState.FaceDetected(count = 1, primaryFace = face)
                        } else {
                            FaceDetectionUiState.FacePositionValid(primaryFace = face)
                        }
                    }
                }
            }
        }
        _faceDetectionState.value = newState
    }

    private fun runRecognition(sourceBitmap: Bitmap, face: FaceDetectionResult, staffId: String) {
        viewModelScope.launch {
            _identityVerificationState.value = StaffBiometricVerificationState.Aligning
            val result = recognitionEngine?.verifyStaffIdentity(sourceBitmap, face, staffId)
                ?: StaffBiometricVerificationState.Unavailable("Recognition engine not configured")
            _identityVerificationState.value = result
        }
    }

    fun refreshLocation() {
        geofenceRepository.startLocationMonitoring()
    }

    fun hasLocationPermission(): Boolean = geofenceRepository.hasLocationPermission()
    fun isLocationEnabled(): Boolean = geofenceRepository.isLocationEnabled()

    fun isLocationVerifiedForAttendance(): Boolean {
        return when (verificationResult.value) {
            is LocationVerificationResult.InsideGeofence, is LocationVerificationResult.Boundary -> true
            else -> false
        }
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
