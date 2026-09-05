package com.governence.faflow.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.governence.faflow.camera.CameraController
import com.governence.faflow.camera.CameraOverlay
import com.governence.faflow.camera.CameraPreviewView
import com.governence.faflow.face.alignment.UmeyamaFaceAligner
import com.governence.faflow.face.embedding.ArcFaceEmbedder
import com.governence.faflow.face.enrollment.LocalFaceEnrollmentRepository
import com.governence.faflow.face.liveness.LivenessEngine
import com.governence.faflow.face.matching.CosineFaceMatcher
import com.governence.faflow.face.model.ArcFaceModelManager
import com.governence.faflow.face.model.ScrfdModelManager
import com.governence.faflow.face.recognition.FaceRecognitionEngine
import com.governence.faflow.face.scrfd.ScrfdFaceDetector
import com.governence.faflow.location.LocationVerificationResult
import com.governence.faflow.ui.components.FaflowPillButton
import com.governence.faflow.ui.components.FaflowProgressStep
import com.governence.faflow.ui.components.FaflowStatusBadge
import com.governence.faflow.ui.components.FaflowSurface
import com.governence.faflow.ui.theme.FaflowShapes
import com.governence.faflow.ui.theme.FaflowSpacing
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.StatusError
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.theme.StatusWarning
import com.governence.faflow.ui.viewmodels.AttendanceEligibilityState
import com.governence.faflow.ui.viewmodels.AttendanceViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern, Calm 3-Step Attendance Check-In / Check-Out Screen for FAFLOW.
 * Hides low-level technical complexity behind human-friendly microcopy:
 * 1. Verify location
 * 2. Verify identity
 * 3. Confirm attendance
 */
@Composable
fun AttendanceCheckInOutScreen(
    viewModel: AttendanceViewModel,
    staffId: String = "",
    onNavigateBack: () -> Unit,
    onAttendanceSuccess: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val verificationResult by viewModel.verificationResult.collectAsState()
    val faceDetectionState by viewModel.faceDetectionState.collectAsState()
    val livenessState by viewModel.livenessState.collectAsState()
    val eligibilityState by viewModel.attendanceEligibilityState.collectAsState()
    val isLocationVerified = viewModel.isLocationVerifiedForAttendance()

    // Model Managers & Face AI Subsystem
    val scrfdModelManager = remember { ScrfdModelManager(context) }
    val faceDetector = remember { ScrfdFaceDetector(scrfdModelManager) }
    val arcFaceModelManager = remember { ArcFaceModelManager(context) }
    val faceEmbedder = remember { ArcFaceEmbedder(arcFaceModelManager) }
    val aligner = remember { UmeyamaFaceAligner() }
    val enrollmentRepo = remember { LocalFaceEnrollmentRepository(context) }
    val matcher = remember { CosineFaceMatcher() }
    val livenessEngine = remember { LivenessEngine() }

    val recognitionEngine = remember {
        FaceRecognitionEngine(
            aligner = aligner,
            embedder = faceEmbedder,
            matcher = matcher,
            enrollmentRepository = enrollmentRepo,
            livenessEngine = livenessEngine
        )
    }

    val latencyMs by faceDetector.inferenceLatencyMs.collectAsState()
    val detections by faceDetector.latestDetections.collectAsState()

    LaunchedEffect(Unit) {
        scrfdModelManager.initializeModels()
        arcFaceModelManager.initializeModels()
    }

    LaunchedEffect(detections) {
        val face = detections.firstOrNull()
        viewModel.updateDetections(
            detections = detections,
            sourceBitmap = face?.alignedBitmap,
            staffId = staffId
        )
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val cameraController = remember {
        CameraController(context = context, frameProcessor = faceDetector, targetFps = 10)
    }
    val cameraState by cameraController.cameraState.collectAsState()

    val currentTimeFormatted = remember {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
    }

    // Step calculation: 1 = Location, 2 = Identity, 3 = Confirmed
    val step1Complete = isLocationVerified
    val step2Complete = eligibilityState is AttendanceEligibilityState.VerifiedAndReady ||
            eligibilityState is AttendanceEligibilityState.ServerAccepted ||
            eligibilityState is AttendanceEligibilityState.SavedOffline
    val step3Complete = eligibilityState is AttendanceEligibilityState.ServerAccepted ||
            eligibilityState is AttendanceEligibilityState.SavedOffline

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FaflowSpacing.md, vertical = FaflowSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.isCheckingIn) "Check In" else "Check Out",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentTimeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.toggleDebugOverlay() }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Options",
                        tint = if (uiState.isDebugOverlayVisible) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FaflowSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 3-Step Progress Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FaflowSpacing.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FaflowProgressStep(
                    stepNumber = 1,
                    label = "Location",
                    isActive = !step1Complete,
                    isCompleted = step1Complete
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(if (step1Complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
                FaflowProgressStep(
                    stepNumber = 2,
                    label = "Identity",
                    isActive = step1Complete && !step2Complete,
                    isCompleted = step2Complete
                )
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(1.dp)
                        .background(if (step2Complete) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
                FaflowProgressStep(
                    stepNumber = 3,
                    label = "Confirm",
                    isActive = step2Complete && !step3Complete,
                    isCompleted = step3Complete
                )
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.md))

            // Contextual Location Indicator
            val (locationTitle, locationSub, locationColor) = when {
                isLocationVerified -> Triple("Location verified", "Campus Perimeter (Testing Bypass Active)", StatusSuccess)
                else -> when (val res = verificationResult) {
                    is LocationVerificationResult.InsideGeofence -> Triple("Location verified", res.geofenceName, StatusSuccess)
                    is LocationVerificationResult.Boundary -> Triple("Location verified", "${res.geofenceName} (Perimeter)", StatusSuccess)
                    is LocationVerificationResult.OutsideAllGeofences -> Triple("You're outside the campus area", "${res.distanceToNearestMeters.toInt()}m from boundary", StatusWarning)
                    is LocationVerificationResult.AccuracyInsufficient -> Triple("Improving location accuracy…", "Please wait for GPS calibration", StatusWarning)
                    is LocationVerificationResult.MockLocationDetected -> Triple("Location cannot be verified", "Simulated location detected", StatusError)
                    is LocationVerificationResult.PermissionDenied -> Triple("Location permission needed", "Tap to grant location access", StatusWarning)
                    LocationVerificationResult.Loading -> Triple("Checking your location…", "Connecting to GPS satellites", MaterialTheme.colorScheme.primary)
                    else -> Triple("Locating campus perimeter…", "Acquiring location", MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FaflowShapes.medium)
                    .background(locationColor.copy(alpha = 0.08f))
                    .padding(horizontal = FaflowSpacing.md, vertical = FaflowSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(locationColor)
                )
                Spacer(modifier = Modifier.width(FaflowSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = locationTitle,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = locationColor
                    )
                    Text(
                        text = locationSub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (verificationResult is LocationVerificationResult.OutsideAllGeofences ||
                    verificationResult is LocationVerificationResult.AccuracyInsufficient
                ) {
                    IconButton(
                        onClick = { viewModel.refreshLocation() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry Location",
                            tint = locationColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.md))

            // Main Viewfinder / Guidance Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isLocationVerified && hasCameraPermission) Color.Black else MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = if (isLocationVerified && hasCameraPermission) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    !isLocationVerified -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(FaflowSpacing.xl)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(StatusWarning.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = StatusWarning,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(FaflowSpacing.md))
                            Text(
                                text = "Campus Presence Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                            Text(
                                text = "To maintain attendance integrity, verification activates when you are physically within campus boundaries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(FaflowSpacing.lg))
                            FaflowPillButton(
                                text = "Refresh Location",
                                onClick = { viewModel.refreshLocation() },
                                icon = Icons.Default.Refresh,
                                isPrimary = false
                            )
                        }
                    }

                    !hasCameraPermission -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(FaflowSpacing.xl)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(FaflowSpacing.md))
                            Text(
                                text = "Camera permission is required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                            Text(
                                text = "Used strictly on-device to verify your identity. Your image is never stored or shared externally.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(FaflowSpacing.lg))
                            FaflowPillButton(
                                text = "Enable Camera",
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                isPrimary = true
                            )
                        }
                    }

                    else -> {
                        // Active Camera with overlay
                        CameraPreviewView(
                            cameraController = cameraController,
                            modifier = Modifier.fillMaxSize()
                        )

                        CameraOverlay(
                            cameraState = cameraState,
                            faceDetectionState = faceDetectionState,
                            livenessState = livenessState,
                            showDebugOverlay = uiState.isDebugOverlayVisible,
                            inferenceLatencyMs = latencyMs,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.md))

            // Action / Confirmation Container
            when (val elig = eligibilityState) {
                is AttendanceEligibilityState.VerifiedAndReady -> {
                    FaflowSurface(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = StatusSuccess.copy(alpha = 0.08f),
                        borderColor = StatusSuccess.copy(alpha = 0.3f),
                        contentPadding = PaddingValues(FaflowSpacing.md)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusSuccess,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(FaflowSpacing.xs))
                                Text(
                                    text = "Identity verified",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusSuccess
                                )
                            }
                            Spacer(modifier = Modifier.height(FaflowSpacing.sm))
                            FaflowPillButton(
                                text = if (uiState.isCheckingIn) "Confirm Check In" else "Confirm Check Out",
                                onClick = {
                                    val staffIdInt = staffId.toIntOrNull()
                                    if (staffIdInt != null && staffIdInt > 0) {
                                        if (uiState.isCheckingIn) {
                                            viewModel.performCheckIn(staffUserId = staffIdInt, onSuccess = onAttendanceSuccess)
                                        } else {
                                            viewModel.performCheckOut(staffUserId = staffIdInt, onSuccess = onAttendanceSuccess)
                                        }
                                    }
                                },
                                isPrimary = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                is AttendanceEligibilityState.Submitting -> {
                    FaflowSurface(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(FaflowSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PrimaryBlue,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(FaflowSpacing.md))
                            Text(
                                text = "Recording attendance…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                is AttendanceEligibilityState.ServerAccepted -> {
                    FaflowSurface(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = StatusSuccess.copy(alpha = 0.08f),
                        borderColor = StatusSuccess.copy(alpha = 0.3f),
                        contentPadding = PaddingValues(FaflowSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = StatusSuccess,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(FaflowSpacing.md))
                            Column {
                                Text(
                                    text = "Attendance recorded",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusSuccess
                                )
                                Text(
                                    text = "Shift confirmed successfully",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is AttendanceEligibilityState.SavedOffline -> {
                    FaflowSurface(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = StatusWarning.copy(alpha = 0.08f),
                        borderColor = StatusWarning.copy(alpha = 0.3f),
                        contentPadding = PaddingValues(FaflowSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = StatusWarning,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(FaflowSpacing.md))
                            Column {
                                Text(
                                    text = "Saved offline",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusWarning
                                )
                                Text(
                                    text = "Will automatically sync when internet connects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is AttendanceEligibilityState.Blocked -> {
                    FaflowSurface(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = StatusError.copy(alpha = 0.08f),
                        borderColor = StatusError.copy(alpha = 0.3f),
                        contentPadding = PaddingValues(FaflowSpacing.md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = StatusError,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(FaflowSpacing.md))
                            Column {
                                Text(
                                    text = "Unable to record attendance",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusError
                                )
                                Text(
                                    text = elig.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                else -> {
                    if (isLocationVerified && hasCameraPermission) {
                        FaflowSurface(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(FaflowSpacing.md)
                        ) {
                            val promptText = when (elig) {
                                AttendanceEligibilityState.SingleFaceRequired -> "Only one person should be in the frame"
                                AttendanceEligibilityState.FaceRequired -> "Position your face inside the guide ring"
                                AttendanceEligibilityState.IdentityVerificationRequired -> "Verifying identity…"
                                AttendanceEligibilityState.LivenessRequired -> "Look straight and blink naturally"
                                else -> "Align face in preview to check in"
                            }
                            Text(
                                text = promptText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.lg))
        }
    }
}
