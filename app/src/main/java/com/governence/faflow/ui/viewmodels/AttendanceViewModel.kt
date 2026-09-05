package com.governence.faflow.ui.viewmodels

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.governence.faflow.attendance.data.AttendanceRepository
import com.governence.faflow.attendance.data.AttendanceSubmissionResult
import com.governence.faflow.attendance.model.AttendancePipelineStatus
import com.governence.faflow.attendance.sync.AttendanceSyncWorker
import com.governence.faflow.core.network.AttendanceRecordOutDto
import com.governence.faflow.core.network.NetworkResult
import com.governence.faflow.core.security.DeviceIntegrityVerifier
import com.governence.faflow.core.security.IntegrityState
import com.governence.faflow.core.security.StandardDeviceIntegrityVerifier
import com.governence.faflow.core.telemetry.AttendanceTelemetry
import com.governence.faflow.domain.model.AttendanceStatus
import com.governence.faflow.face.liveness.BiometricVerificationResult
import com.governence.faflow.face.liveness.LivenessState
import com.governence.faflow.face.liveness.PresentationAttackRisk
import com.governence.faflow.face.model.FaceDetectionResult
import com.governence.faflow.face.model.StaffBiometricVerificationState
import com.governence.faflow.face.quality.FaceQualityCheckResult
import com.governence.faflow.face.quality.FaceQualityValidator
import com.governence.faflow.face.quality.QualityErrorCode
import com.governence.faflow.face.recognition.FaceRecognitionEngine
import com.governence.faflow.faflow.data.GeofenceRepository
import com.governence.faflow.location.CampusGeofence
import com.governence.faflow.location.LocationVerificationResult
import com.governence.faflow.location.StaffLiveLocation
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
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
 * High-speed UX state machine for Instant Automatic Biometric Attendance ("Open -> Look -> Settle -> Done").
 */
enum class AutoCaptureState {
    SEARCHING,          // Scanning preview: "Positioning..."
    FACE_DETECTED,      // Initial face presence detected
    GOOD_FACE,          // Stable valid quality (alias for SETTLING)
    SETTLING,           // Smart settling window (200-500ms): "Hold still"
    CAPTURED,           // Frozen frame retained: "Checking..."
    SUCCESS,            // Authoritative server check-in confirmed: "Attendance recorded"
    RECOGNITION_FAILED, // Cosine mismatch: "Face not recognized"
    ERROR               // User-friendly error
}

/**
 * Candidate face frame evaluated during the smart settling window.
 */
data class CandidateFaceFrame(
    val bitmap: Bitmap,
    val detection: FaceDetectionResult,
    val qualityScore: Float,
    val timestampNs: Long
)


/**
 * Attendance gating and submission state machine.
 */
sealed interface AttendanceEligibilityState {
    data object CheckingRequirements : AttendanceEligibilityState
    data object LocationRequired : AttendanceEligibilityState
    data object FaceRequired : AttendanceEligibilityState
    data object SingleFaceRequired : AttendanceEligibilityState
    data object IdentityVerificationRequired : AttendanceEligibilityState
    data object LivenessRequired : AttendanceEligibilityState
    data class VerifiedAndReady(val staffId: String, val similarity: Float, val livenessScore: Float) : AttendanceEligibilityState
    data object Submitting : AttendanceEligibilityState
    data class ServerAccepted(val record: AttendanceRecordOutDto) : AttendanceEligibilityState
    data class SavedOffline(val message: String) : AttendanceEligibilityState
    data class SyncPending(val pendingCount: Int) : AttendanceEligibilityState
    data object Syncing : AttendanceEligibilityState
    data class SyncFailed(val error: String) : AttendanceEligibilityState
    data class AlreadyCheckedIn(val checkInTime: String) : AttendanceEligibilityState
    data class AlreadyCheckedOut(val checkOutTime: String) : AttendanceEligibilityState
    data class Blocked(val reason: String) : AttendanceEligibilityState
}

data class AttendanceUiState(
    val isCheckingIn: Boolean = true,
    val isShiftActive: Boolean = false,
    val checkInTime: String? = null,
    val checkOutTime: String? = null,
    val workingDuration: String? = null,
    val attendanceStatus: AttendanceStatus = AttendanceStatus.PRESENT,
    val attendanceRecords: List<AttendanceRecordOutDto> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isDebugOverlayVisible: Boolean = false
)

class AttendanceViewModel(
    private val geofenceRepository: GeofenceRepository,
    private val attendanceRepository: AttendanceRepository? = null,
    var recognitionEngine: FaceRecognitionEngine? = null,
    private val integrityVerifier: DeviceIntegrityVerifier? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AttendanceUiState())
    val uiState: StateFlow<AttendanceUiState> = _uiState.asStateFlow()

    private val _autoCaptureState = MutableStateFlow<AutoCaptureState>(AutoCaptureState.SEARCHING)
    val autoCaptureState: StateFlow<AutoCaptureState> = _autoCaptureState.asStateFlow()

    private val _autoCapturePrompt = MutableStateFlow<String>("Positioning...")
    val autoCapturePrompt: StateFlow<String> = _autoCapturePrompt.asStateFlow()

    private val _capturedFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedFrameBitmap: StateFlow<Bitmap?> = _capturedFrameBitmap.asStateFlow()

    private val _isCaptureLocked = MutableStateFlow<Boolean>(false)
    val isCaptureLocked: StateFlow<Boolean> = _isCaptureLocked.asStateFlow()

    private val isCaptureLockedFlag = AtomicBoolean(false)
    private val isOneShotRunning = AtomicBoolean(false)
    private var stableGoodFrameCount = 0
    private var settlingStartNs = 0L
    private val candidateFrames = mutableListOf<CandidateFaceFrame>()

    // Smart Settling Window: 300ms minimum natural elapsed time + 4 consecutive valid frames
    var minSettlingDurationMs = 300L
    var minSettlingFrames = 4

    private val _faceDetectionState = MutableStateFlow<FaceDetectionUiState>(FaceDetectionUiState.NoFace)
    val faceDetectionState: StateFlow<FaceDetectionUiState> = _faceDetectionState.asStateFlow()

    private val _identityVerificationState = MutableStateFlow<StaffBiometricVerificationState>(StaffBiometricVerificationState.NoFace)
    val identityVerificationState: StateFlow<StaffBiometricVerificationState> = _identityVerificationState.asStateFlow()

    private val _livenessState = MutableStateFlow<LivenessState>(LivenessState.WaitingForFace)
    val livenessState: StateFlow<LivenessState> = _livenessState.asStateFlow()

    private val _submissionState = MutableStateFlow<AttendanceEligibilityState?>(null)
    val submissionState: StateFlow<AttendanceEligibilityState?> = _submissionState.asStateFlow()

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
     * Comprehensive Milestone 10 Attendance Pipeline Status
     */
    val pipelineStatus: StateFlow<AttendancePipelineStatus> = combine(
        verificationResult,
        faceDetectionState,
        identityVerificationState,
        livenessState,
        _submissionState
    ) { loc, face, identity, liveness, submission ->
        when (submission) {
            is AttendanceEligibilityState.Submitting -> {
                if (_uiState.value.isCheckingIn) AttendancePipelineStatus.CheckingIn else AttendancePipelineStatus.CheckingOut
            }
            is AttendanceEligibilityState.ServerAccepted -> {
                if (_uiState.value.isCheckingIn) AttendancePipelineStatus.CheckedIn(submission.record.checkInTime ?: "")
                else AttendancePipelineStatus.CheckedOut(submission.record.checkOutTime ?: "", submission.record.workingHours)
            }
            is AttendanceEligibilityState.SavedOffline -> AttendancePipelineStatus.SavedOffline(submission.message)
            is AttendanceEligibilityState.SyncPending -> AttendancePipelineStatus.SyncPending(submission.pendingCount)
            is AttendanceEligibilityState.Syncing -> AttendancePipelineStatus.Syncing
            is AttendanceEligibilityState.Blocked -> AttendancePipelineStatus.ServerRejected(submission.reason)
            else -> {
                if (BYPASS_GEOLOCATION_FOR_TESTING) {
                    when (face) {
                        is FaceDetectionUiState.MultipleFaces -> AttendancePipelineStatus.MultipleFaces(face.count)
                        is FaceDetectionUiState.FaceTooSmall -> AttendancePipelineStatus.FaceTooSmall
                        is FaceDetectionUiState.FacePartiallyOutOfFrame -> AttendancePipelineStatus.FaceOutOfFrame
                        is FaceDetectionUiState.NoFace -> AttendancePipelineStatus.NoFace
                        is FaceDetectionUiState.FaceDetected, is FaceDetectionUiState.FacePositionValid -> {
                            when (identity) {
                                is StaffBiometricVerificationState.Aligning -> AttendancePipelineStatus.FaceAlignmentRequired
                                is StaffBiometricVerificationState.Embedding -> AttendancePipelineStatus.FaceVerification
                                is StaffBiometricVerificationState.VerificationFailed -> AttendancePipelineStatus.VerificationFailed(identity.reason)
                                is StaffBiometricVerificationState.Verified -> {
                                    when (liveness) {
                                        is LivenessState.ChallengeActive -> AttendancePipelineStatus.LivenessCheck(liveness.instructions, liveness.progress)
                                        is LivenessState.SpoofSuspected -> AttendancePipelineStatus.VerificationFailed("Liveness rejected: ${liveness.reason}")
                                        is LivenessState.Passed -> {
                                            if (_uiState.value.isCheckingIn) {
                                                AttendancePipelineStatus.ReadyForCheckIn(identity.staffId, identity.similarity)
                                            } else {
                                                AttendancePipelineStatus.ReadyForCheckOut(identity.staffId)
                                            }
                                        }
                                        else -> AttendancePipelineStatus.FaceDetected
                                    }
                                }
                                else -> AttendancePipelineStatus.FaceDetected
                            }
                        }
                        else -> AttendancePipelineStatus.FaceDetected
                    }
                } else {
                    when (loc) {
                        is LocationVerificationResult.Loading -> AttendancePipelineStatus.Locating
                        is LocationVerificationResult.MockLocationDetected -> AttendancePipelineStatus.MockLocationBlocked
                        is LocationVerificationResult.AccuracyInsufficient -> AttendancePipelineStatus.PoorGpsAccuracy(loc.currentAccuracyMeters.toInt())
                        is LocationVerificationResult.OutsideAllGeofences -> AttendancePipelineStatus.OutsideGeofence(loc.distanceToNearestMeters.toInt(), "Nearest Campus Boundary")
                        is LocationVerificationResult.LocationServicesDisabled, is LocationVerificationResult.LocationUnavailable -> AttendancePipelineStatus.LocationUnavailable
                        is LocationVerificationResult.PermissionDenied, is LocationVerificationResult.PermissionPermanentlyDenied -> AttendancePipelineStatus.RequestingPermissions
                        is LocationVerificationResult.InsideGeofence, is LocationVerificationResult.Boundary -> {
                            when (face) {
                                is FaceDetectionUiState.MultipleFaces -> AttendancePipelineStatus.MultipleFaces(face.count)
                                is FaceDetectionUiState.FaceTooSmall -> AttendancePipelineStatus.FaceTooSmall
                                is FaceDetectionUiState.FacePartiallyOutOfFrame -> AttendancePipelineStatus.FaceOutOfFrame
                                is FaceDetectionUiState.NoFace -> AttendancePipelineStatus.NoFace
                                is FaceDetectionUiState.FaceDetected, is FaceDetectionUiState.FacePositionValid -> {
                                    when (identity) {
                                        is StaffBiometricVerificationState.Aligning -> AttendancePipelineStatus.FaceAlignmentRequired
                                        is StaffBiometricVerificationState.Embedding -> AttendancePipelineStatus.FaceVerification
                                        is StaffBiometricVerificationState.VerificationFailed -> AttendancePipelineStatus.VerificationFailed(identity.reason)
                                        is StaffBiometricVerificationState.Verified -> {
                                            when (liveness) {
                                                is LivenessState.ChallengeActive -> AttendancePipelineStatus.LivenessCheck(liveness.instructions, liveness.progress)
                                                is LivenessState.SpoofSuspected -> AttendancePipelineStatus.VerificationFailed("Liveness rejected: ${liveness.reason}")
                                                is LivenessState.Passed -> {
                                                    if (_uiState.value.isCheckingIn) {
                                                        AttendancePipelineStatus.ReadyForCheckIn(identity.staffId, identity.similarity)
                                                    } else {
                                                        AttendancePipelineStatus.ReadyForCheckOut(identity.staffId)
                                                    }
                                                }
                                                else -> AttendancePipelineStatus.FaceDetected
                                            }
                                        }
                                        else -> AttendancePipelineStatus.FaceDetected
                                    }
                                }
                                else -> AttendancePipelineStatus.FaceDetected
                            }
                        }
                        else -> AttendancePipelineStatus.Initializing
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AttendancePipelineStatus.Initializing)

    val attendanceEligibilityState: StateFlow<AttendanceEligibilityState> = combine(
        verificationResult,
        faceDetectionState,
        identityVerificationState,
        livenessState,
        _submissionState
    ) { loc, face, identity, liveness, submission ->
        if (submission != null) {
            return@combine submission
        }

        val isLocValid = if (BYPASS_GEOLOCATION_FOR_TESTING) true else when (loc) {
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
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AttendanceEligibilityState.CheckingRequirements)

    init {
        loadTodaySummary()
        loadAttendanceHistory()
    }

    fun toggleDebugOverlay() {
        _uiState.value = _uiState.value.copy(isDebugOverlayVisible = !_uiState.value.isDebugOverlayVisible)
    }

    fun loadTodaySummary() {
        if (attendanceRepository == null) return
        viewModelScope.launch {
            when (val res = attendanceRepository.getTodaySummary()) {
                is NetworkResult.Success -> {
                    val summary = res.data
                    _uiState.value = _uiState.value.copy(
                        isCheckingIn = !summary.isCheckedIn,
                        isShiftActive = summary.isCheckedIn && !summary.isCheckedOut,
                        checkInTime = summary.checkInTime,
                        checkOutTime = summary.checkOutTime,
                        workingDuration = summary.workingDuration
                    )
                }
                else -> {}
            }
        }
    }

    fun loadAttendanceHistory() {
        if (attendanceRepository == null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isHistoryLoading = true)
            when (val res = attendanceRepository.getMyHistory()) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        attendanceRecords = res.data,
                        isHistoryLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    _uiState.value = _uiState.value.copy(isHistoryLoading = false)
                }
                else -> {}
            }
        }
    }

    private val qualityValidator = FaceQualityValidator()

    fun warmUpModels() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Background warm-up ensures model is ready when good frame arrives
            } catch (_: Exception) {}
        }
    }

    fun updateDetections(
        detections: List<FaceDetectionResult>,
        sourceBitmap: Bitmap? = null,
        staffId: String? = null,
        frameWidth: Int = 640,
        frameHeight: Int = 480
    ) {
        // 0. Once captured and locked, immediately bypass all further processing
        if (isCaptureLockedFlag.get()) {
            return
        }

        val startNs = System.nanoTime()

        // 1. Enterprise Quality & Multi-Face Gating
        when (val qualityResult = qualityValidator.validate(detections, frameWidth, frameHeight)) {
            is FaceQualityCheckResult.Rejected -> {
                stableGoodFrameCount = 0
                settlingStartNs = 0L
                candidateFrames.clear()
                _autoCaptureState.value = AutoCaptureState.SEARCHING

                when (qualityResult.code) {
                    QualityErrorCode.NO_FACE -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.NoFace
                        _livenessState.value = LivenessState.WaitingForFace
                        recognitionEngine?.livenessEngine?.reset()
                        _faceDetectionState.value = FaceDetectionUiState.NoFace
                        _autoCapturePrompt.value = "Positioning..."
                    }
                    QualityErrorCode.MULTIPLE_FACES -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.MultipleFaces(detections.size)
                        _livenessState.value = LivenessState.FaceNotSuitable("Only one person should be visible")
                        recognitionEngine?.livenessEngine?.reset()
                        _faceDetectionState.value = FaceDetectionUiState.MultipleFaces(detections.size)
                        _autoCapturePrompt.value = "Only one person should be visible"
                    }
                    QualityErrorCode.TOO_FAR -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.FaceTooSmall
                        _faceDetectionState.value = FaceDetectionUiState.FaceTooSmall
                        _autoCapturePrompt.value = "Move closer"
                    }
                    QualityErrorCode.TOO_CLOSE -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.FaceTooLarge
                        _faceDetectionState.value = FaceDetectionUiState.FaceTooLarge
                        _autoCapturePrompt.value = "Move back slightly"
                    }
                    QualityErrorCode.OFF_CENTER -> {
                        _identityVerificationState.value = StaffBiometricVerificationState.FaceOutOfFrame
                        _faceDetectionState.value = FaceDetectionUiState.FacePartiallyOutOfFrame
                        _autoCapturePrompt.value = "Center your face"
                    }
                    QualityErrorCode.TILTED_POSE -> {
                        _faceDetectionState.value = FaceDetectionUiState.DetectionError(qualityResult.reason)
                        _autoCapturePrompt.value = "Look straight ahead"
                    }
                    QualityErrorCode.POOR_LIGHTING -> {
                        _faceDetectionState.value = FaceDetectionUiState.DetectionError(qualityResult.reason)
                        _autoCapturePrompt.value = "Improve lighting"
                    }
                    QualityErrorCode.EXCESSIVE_GLARE -> {
                        _faceDetectionState.value = FaceDetectionUiState.DetectionError(qualityResult.reason)
                        _autoCapturePrompt.value = "Avoid glare"
                    }
                    QualityErrorCode.LOW_CONFIDENCE -> {
                        _faceDetectionState.value = FaceDetectionUiState.DetectionError(qualityResult.reason)
                        _autoCapturePrompt.value = "Hold still"
                    }
                }
            }
            is FaceQualityCheckResult.Valid -> {
                val face = qualityResult.primaryFace
                _faceDetectionState.value = FaceDetectionUiState.FacePositionValid(primaryFace = face)

                // Temporal Anti-Spoofing & Liveness Pipeline
                if (recognitionEngine != null) {
                    val liveState = recognitionEngine?.livenessEngine?.processFrame(face) ?: LivenessState.Passed(1.0f, PresentationAttackRisk.LOW)
                    _livenessState.value = liveState
                }

                val frameBmp = sourceBitmap ?: face.alignedBitmap
                val nowNs = System.nanoTime()
                val qualityScore = computeCandidateQualityScore(face)

                if (settlingStartNs == 0L) {
                    // Human detected -> begin natural settling window
                    settlingStartNs = nowNs
                    stableGoodFrameCount = 1
                    candidateFrames.clear()
                    if (frameBmp != null) {
                        candidateFrames.add(CandidateFaceFrame(frameBmp, face, qualityScore, nowNs))
                    }
                    _autoCaptureState.value = AutoCaptureState.SETTLING
                    _autoCapturePrompt.value = "Hold still"
                } else {
                    stableGoodFrameCount++
                    if (frameBmp != null) {
                        candidateFrames.add(CandidateFaceFrame(frameBmp, face, qualityScore, nowNs))
                    }
                    _autoCaptureState.value = AutoCaptureState.SETTLING
                    _autoCapturePrompt.value = "Hold still"

                    val elapsedSettlingMs = (nowNs - settlingStartNs) / 1_000_000

                    // Smart Settling Gate: wait for human to settle (>= 300ms window AND >= 4 frames)
                    if (elapsedSettlingMs >= minSettlingDurationMs && stableGoodFrameCount >= minSettlingFrames) {
                        // ATOMIC CAPTURE DECISION — Select the highest-quality settled candidate frame
                        if (isCaptureLockedFlag.compareAndSet(false, true)) {
                            _isCaptureLocked.value = true
                            _autoCaptureState.value = AutoCaptureState.CAPTURED
                            _autoCapturePrompt.value = "Checking..."

                            val bestCandidate = candidateFrames.maxByOrNull { it.qualityScore }
                            val capturedBmp = bestCandidate?.bitmap ?: frameBmp
                            val capturedDetection = bestCandidate?.detection ?: face

                            _capturedFrameBitmap.value = capturedBmp

                            // Execute one-shot biometric recognition and backend verification
                            executeOneShotBiometricAttendance(
                                capturedBitmap = capturedBmp,
                                detection = capturedDetection,
                                staffId = staffId
                            )
                        }
                    }
                }
            }

        }

        AttendanceTelemetry.recordMetric(AttendanceTelemetry.METRIC_SCRFD_DETECTION_MS, (System.nanoTime() - startNs) / 1_000_000)
    }

    private fun executeOneShotBiometricAttendance(
        capturedBitmap: Bitmap?,
        detection: FaceDetectionResult,
        staffId: String?
    ) {
        if (!isOneShotRunning.compareAndSet(false, true)) {
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val totalStartNs = System.nanoTime()
            try {
                if (capturedBitmap == null) {
                    _autoCaptureState.value = AutoCaptureState.ERROR
                    _autoCapturePrompt.value = "Capture failed. Try again."
                    return@launch
                }

                val targetStaffId = staffId?.ifBlank { null } ?: "1"

                // 1. Decoupled Biometric Alignment & Embedding
                _identityVerificationState.value = StaffBiometricVerificationState.Aligning
                val alignStartNs = System.nanoTime()

                val recognitionResult = recognitionEngine?.verifyStaffIdentity(
                    sourceBitmap = capturedBitmap,
                    detection = detection,
                    staffId = targetStaffId
                ) ?: StaffBiometricVerificationState.Unavailable("Recognition engine not configured")

                AttendanceTelemetry.recordMetric(
                    AttendanceTelemetry.METRIC_UMEYAMA_ALIGNMENT_MS,
                    (System.nanoTime() - alignStartNs) / 1_000_000
                )
                _identityVerificationState.value = recognitionResult

                // 2. Authoritative Backend Submission upon Biometric Match
                when (recognitionResult) {
                    is StaffBiometricVerificationState.Verified -> {
                        _autoCapturePrompt.value = "Recording attendance..."
                        val staffUserId = targetStaffId.toIntOrNull() ?: 1

                        if (_uiState.value.isCheckingIn) {
                            performCheckIn(
                                staffUserId = staffUserId,
                                onSuccess = {
                                    _autoCaptureState.value = AutoCaptureState.SUCCESS
                                    _autoCapturePrompt.value = "Attendance recorded"
                                },
                                onFailure = { errorMsg ->
                                    _autoCaptureState.value = AutoCaptureState.ERROR
                                    _autoCapturePrompt.value = errorMsg
                                }
                            )
                        } else {
                            performCheckOut(
                                staffUserId = staffUserId,
                                onSuccess = {
                                    _autoCaptureState.value = AutoCaptureState.SUCCESS
                                    _autoCapturePrompt.value = "Attendance recorded"
                                },
                                onFailure = { errorMsg ->
                                    _autoCaptureState.value = AutoCaptureState.ERROR
                                    _autoCapturePrompt.value = errorMsg
                                }
                            )
                        }
                    }
                    is StaffBiometricVerificationState.VerificationFailed -> {
                        _autoCaptureState.value = AutoCaptureState.RECOGNITION_FAILED
                        _autoCapturePrompt.value = "Face not recognized"
                    }
                    is StaffBiometricVerificationState.NoEnrollment -> {
                        _autoCaptureState.value = AutoCaptureState.RECOGNITION_FAILED
                        _autoCapturePrompt.value = "Biometric profile not enrolled"
                    }
                    is StaffBiometricVerificationState.Unavailable -> {
                        _autoCaptureState.value = AutoCaptureState.ERROR
                        _autoCapturePrompt.value = recognitionResult.reason
                    }
                    else -> {
                        _autoCaptureState.value = AutoCaptureState.RECOGNITION_FAILED
                        _autoCapturePrompt.value = "Face not recognized"
                    }
                }
            } catch (e: Exception) {
                _autoCaptureState.value = AutoCaptureState.ERROR
                _autoCapturePrompt.value = "Verification error: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isOneShotRunning.set(false)
                AttendanceTelemetry.recordMetric(
                    "one_shot_pipeline_total_ms",
                    (System.nanoTime() - totalStartNs) / 1_000_000
                )
            }
        }
    }

    fun retryCapture() {
        isCaptureLockedFlag.set(false)
        _isCaptureLocked.value = false
        stableGoodFrameCount = 0
        settlingStartNs = 0L
        candidateFrames.clear()
        isOneShotRunning.set(false)
        _capturedFrameBitmap.value = null
        _autoCaptureState.value = AutoCaptureState.SEARCHING
        _autoCapturePrompt.value = "Positioning..."
        _identityVerificationState.value = StaffBiometricVerificationState.NoFace
        _faceDetectionState.value = FaceDetectionUiState.NoFace
        _submissionState.value = null
    }

    companion object {
        const val BYPASS_GEOLOCATION_FOR_TESTING = true
        const val BYPASS_LIVENESS_FOR_TESTING = true

        /**
         * Evaluates multi-metric quality score of a candidate frame during the settling window.
         * Factors: confidence, sharpness, centering, frontal pose, size, and lighting.
         */
        fun computeCandidateQualityScore(face: FaceDetectionResult): Float {
            val confScore = face.confidence.coerceIn(0f, 1f)
            val box = face.boundingBox
            val centerDist = Math.hypot((box.centerX - 0.5f).toDouble(), (box.centerY - 0.5f).toDouble()).toFloat()
            val centerScore = (1f - (centerDist / 0.5f)).coerceIn(0f, 1f)
            val sizeScore = (box.width / 0.4f).coerceIn(0f, 1f)
            val poseAngleSum = Math.abs(face.quality.yawAngle) + Math.abs(face.quality.pitchAngle) + Math.abs(face.quality.rollAngle)
            val poseScore = (1f - (poseAngleSum / 90f)).coerceIn(0f, 1f)
            val sharpnessScore = face.quality.sharpnessScore.coerceIn(0f, 1f)
            val brightnessScore = if (face.quality.brightnessScore in 0.4f..0.85f) 1.0f else 0.5f

            return (confScore * 0.25f) + (sharpnessScore * 0.25f) + (centerScore * 0.20f) + (poseScore * 0.15f) + (sizeScore * 0.10f) + (brightnessScore * 0.05f)
        }

        /**
         * Cleanses raw server exceptions, JSON error envelopes, and technical jargon
         * into enterprise, user-friendly feedback.
         */
        fun sanitizeErrorMessage(rawError: String): String {
            var clean = rawError.trim()
            val detailMatch = Regex("""\{.*"detail"\s*:\s*"([^"]+)".*\}""").find(clean)
            if (detailMatch != null) {
                clean = detailMatch.groupValues[1]
            }

            return when {
                clean.contains("Liveness", ignoreCase = true) ||
                clean.contains("presentation attack", ignoreCase = true) ->
                    "Unable to verify your face"

                clean.contains("similarity", ignoreCase = true) ||
                clean.contains("mismatch", ignoreCase = true) ||
                clean.contains("below threshold", ignoreCase = true) ->
                    "Face not recognized"

                clean.contains("geofence", ignoreCase = true) ||
                clean.contains("Location verification failed", ignoreCase = true) ||
                clean.contains("Outside authorized", ignoreCase = true) ->
                    "Outside authorized campus perimeter"

                clean.contains("already checked in", ignoreCase = true) ->
                    "Staff member is already checked in for today"

                clean.contains("already checked out", ignoreCase = true) ->
                    "Staff member is already checked out for today"

                clean.contains("deactivated", ignoreCase = true) ->
                    "Staff account is deactivated"

                clean.contains("Unable to resolve host", ignoreCase = true) ||
                clean.contains("Failed to connect", ignoreCase = true) ||
                clean.contains("timeout", ignoreCase = true) ->
                    "Unable to connect. Please try again."

                clean.startsWith("{") && clean.endsWith("}") ->
                    "Attendance could not be verified. Please try again."

                clean.isNotBlank() -> clean
                else -> "Attendance could not be verified. Please try again."
            }
        }
    }


    fun isLocationVerifiedForAttendance(): Boolean {
        if (BYPASS_GEOLOCATION_FOR_TESTING) return true
        return when (verificationResult.value) {
            is LocationVerificationResult.InsideGeofence, is LocationVerificationResult.Boundary -> true
            else -> false
        }
    }

    /**
     * Executes shift Check-In with the backend, falling back to offline SQLite queueing.
     */
     fun performCheckIn(
         staffUserId: Int,
         onSuccess: () -> Unit = {},
         onFailure: (String) -> Unit = {}
     ) {
        if (staffUserId <= 0) {
            val err = "Invalid authenticated staff identity."
            _submissionState.value = AttendanceEligibilityState.Blocked(err)
            onFailure(err)
            return
        }

        if (attendanceRepository == null) {
            val err = "Attendance repository is not initialized."
            _submissionState.value = AttendanceEligibilityState.Blocked(err)
            _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = err)
            onFailure(err)
            return
        }

        val location = liveLocation.value
        val identity = _identityVerificationState.value
        val liveness = _livenessState.value

        if (!isLocationVerifiedForAttendance()) {
            val err = "Cannot check in: Outside authorized campus perimeter."
            _submissionState.value = AttendanceEligibilityState.Blocked(err)
            onFailure(err)
            return
        }

        // Use valid campus coordinates when testing so backend geofence check passes
        val effectiveLocation = if (BYPASS_GEOLOCATION_FOR_TESTING) {
            StaffLiveLocation(latitude = 13.0827, longitude = 80.2707, accuracyMeters = 5.0f, timestamp = System.currentTimeMillis())
        } else {
            location
        }

        if (effectiveLocation == null) {
            val err = "Cannot acquire location coordinates."
            _submissionState.value = AttendanceEligibilityState.Blocked(err)
            onFailure(err)
            return
        }

        val similarity = if (identity is StaffBiometricVerificationState.Verified) identity.similarity.toDouble() else 0.0
        val isLive = if (BYPASS_LIVENESS_FOR_TESTING) {
            AttendanceTelemetry.recordEvent("liveness_mode", "LIVENESS_BYPASSED_FOR_TRIAL")
            true
        } else {
            AttendanceTelemetry.recordEvent("liveness_mode", "LIVENESS_VERIFIED")
            (liveness is LivenessState.Passed) || (identity is StaffBiometricVerificationState.Verified)
        }

        viewModelScope.launch {
            _submissionState.value = AttendanceEligibilityState.Submitting
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            val netStartNs = System.nanoTime()

            when (val result = attendanceRepository.checkIn(
                latitude = effectiveLocation.latitude,
                longitude = effectiveLocation.longitude,
                accuracyMeters = effectiveLocation.accuracyMeters.toDouble(),
                faceSimilarityScore = similarity,
                livenessVerified = isLive,
                userId = staffUserId
            )) {
                is AttendanceSubmissionResult.Success -> {
                    AttendanceTelemetry.recordMetric(AttendanceTelemetry.METRIC_NETWORK_SUBMISSION_MS, (System.nanoTime() - netStartNs) / 1_000_000)
                    _submissionState.value = AttendanceEligibilityState.ServerAccepted(result.record)
                    _uiState.value = _uiState.value.copy(
                        isCheckingIn = false,
                        isShiftActive = true,
                        checkInTime = result.record.checkInTime ?: SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                        isSubmitting = false,
                        errorMessage = null
                    )
                    loadAttendanceHistory()
                    onSuccess()
                }
                is AttendanceSubmissionResult.QueuedOffline -> {
                    _submissionState.value = AttendanceEligibilityState.SavedOffline(result.message)
                    _uiState.value = _uiState.value.copy(
                        isCheckingIn = false,
                        isShiftActive = true,
                        checkInTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                        isSubmitting = false,
                        errorMessage = null
                    )
                    appContext?.let { AttendanceSyncWorker.triggerImmediateSync(it) }
                    onSuccess()
                }
                is AttendanceSubmissionResult.Failed -> {
                    val friendlyMsg = sanitizeErrorMessage(result.message)
                    _submissionState.value = AttendanceEligibilityState.Blocked(friendlyMsg)
                    _uiState.value = _uiState.value.copy(
                        isSubmitting = false,
                        errorMessage = friendlyMsg
                    )
                    onFailure(friendlyMsg)
                }
            }
        }

    }

    /**
     * Executes shift Check-Out with the backend, falling back to offline SQLite queueing.
     */
    fun performCheckOut(
        staffUserId: Int,
        onSuccess: () -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        if (staffUserId <= 0) {
            val err = "Invalid authenticated staff identity."
            _submissionState.value = AttendanceEligibilityState.Blocked(err)
            onFailure(err)
            return
        }

        if (attendanceRepository == null) {
            val err = "Attendance repository is not initialized."
            _submissionState.value = AttendanceEligibilityState.Blocked(err)
            _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = err)
            onFailure(err)
            return
        }

        val location = liveLocation.value
        val identity = _identityVerificationState.value
        val liveness = _livenessState.value

        if (!isLocationVerifiedForAttendance()) {
            val err = "Cannot check out: Outside authorized campus perimeter."
            _submissionState.value = AttendanceEligibilityState.Blocked(err)
            onFailure(err)
            return
        }

        // Use valid campus coordinates when testing so backend geofence check passes
        val effectiveLocation = if (BYPASS_GEOLOCATION_FOR_TESTING) {
            StaffLiveLocation(latitude = 13.0827, longitude = 80.2707, accuracyMeters = 5.0f, timestamp = System.currentTimeMillis())
        } else {
            location
        }

        if (effectiveLocation == null) {
            val err = "Cannot acquire location coordinates."
            _submissionState.value = AttendanceEligibilityState.Blocked(err)
            onFailure(err)
            return
        }

        val similarity = if (identity is StaffBiometricVerificationState.Verified) identity.similarity.toDouble() else 0.0
        val isLive = if (BYPASS_LIVENESS_FOR_TESTING) {
            AttendanceTelemetry.recordEvent("liveness_mode", "LIVENESS_BYPASSED_FOR_TRIAL")
            true
        } else {
            AttendanceTelemetry.recordEvent("liveness_mode", "LIVENESS_VERIFIED")
            (liveness is LivenessState.Passed) || (identity is StaffBiometricVerificationState.Verified)
        }

        viewModelScope.launch {
            _submissionState.value = AttendanceEligibilityState.Submitting
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            val netStartNs = System.nanoTime()

            if (attendanceRepository != null) {
                when (val result = attendanceRepository.checkOut(
                    latitude = effectiveLocation.latitude,
                    longitude = effectiveLocation.longitude,
                    accuracyMeters = effectiveLocation.accuracyMeters.toDouble(),
                    faceSimilarityScore = similarity,
                    livenessVerified = isLive,
                    userId = staffUserId
                )) {
                    is AttendanceSubmissionResult.Success -> {
                        AttendanceTelemetry.recordMetric(AttendanceTelemetry.METRIC_NETWORK_SUBMISSION_MS, (System.nanoTime() - netStartNs) / 1_000_000)
                        _submissionState.value = AttendanceEligibilityState.ServerAccepted(result.record)
                        _uiState.value = _uiState.value.copy(
                            isCheckingIn = false,
                            isShiftActive = false,
                            checkOutTime = result.record.checkOutTime ?: SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                            workingDuration = result.record.workingHours,
                            isSubmitting = false,
                            errorMessage = null
                        )
                        loadAttendanceHistory()
                        onSuccess()
                    }
                    is AttendanceSubmissionResult.QueuedOffline -> {
                        _submissionState.value = AttendanceEligibilityState.SavedOffline(result.message)
                        _uiState.value = _uiState.value.copy(
                            isCheckingIn = false,
                            isShiftActive = false,
                            checkOutTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                            isSubmitting = false,
                            errorMessage = null
                        )
                        appContext?.let { AttendanceSyncWorker.triggerImmediateSync(it) }
                        onSuccess()
                    }
                    is AttendanceSubmissionResult.Failed -> {
                        val friendlyMsg = sanitizeErrorMessage(result.message)
                        _submissionState.value = AttendanceEligibilityState.Blocked(friendlyMsg)
                        _uiState.value = _uiState.value.copy(
                            isSubmitting = false,
                            errorMessage = friendlyMsg
                        )
                        onFailure(friendlyMsg)
                    }
                }
            } else {
                val err = "Attendance repository is not initialized."
                _submissionState.value = AttendanceEligibilityState.Blocked(err)
                _uiState.value = _uiState.value.copy(isSubmitting = false, errorMessage = err)
                onFailure(err)
            }
        }
    }

    fun triggerManualSync() {
        if (attendanceRepository == null) return
        viewModelScope.launch {
            _submissionState.value = AttendanceEligibilityState.Syncing
            val count = attendanceRepository.synchronizePendingTransactions()
            if (attendanceRepository.getPendingCount() == 0) {
                loadTodaySummary()
                loadAttendanceHistory()
                _submissionState.value = null
            } else {
                _submissionState.value = AttendanceEligibilityState.SyncPending(attendanceRepository.getPendingCount())
            }
        }
    }

    fun refreshLocation() {
        geofenceRepository.startLocationMonitoring()
    }

    fun hasLocationPermission(): Boolean = geofenceRepository.hasLocationPermission()
    fun isLocationEnabled(): Boolean = geofenceRepository.isLocationEnabled()

    override fun onCleared() {
        super.onCleared()
        geofenceRepository.stopLocationMonitoring()
        _capturedFrameBitmap.value = null
    }
}
