package com.governence.faflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.governence.faflow.location.GeofenceValidationResult
import com.governence.faflow.ui.components.AppTopBar
import com.governence.faflow.ui.components.PrimaryGradientButton
import com.governence.faflow.ui.theme.DarkSurfaceVariant
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.SecondaryTeal
import com.governence.faflow.ui.theme.StatusError
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.theme.StatusWarning
import com.governence.faflow.ui.viewmodels.AttendanceViewModel

@Composable
fun AttendanceCheckInOutScreen(
    viewModel: AttendanceViewModel,
    onNavigateBack: () -> Unit,
    onAttendanceSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val geofenceResult by viewModel.geofenceResult.collectAsState()
    val isInsideGeofence = geofenceResult is GeofenceValidationResult.Inside

    Scaffold(
        topBar = {
            AppTopBar(
                title = if (uiState.isCheckingIn) "Staff Check-In" else "Staff Check-Out",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Geofence Status Banner
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (geofenceResult) {
                        is GeofenceValidationResult.Inside -> Color(0x1A10B981)
                        is GeofenceValidationResult.MockLocationDetected -> Color(0x1AEF4444)
                        is GeofenceValidationResult.PoorAccuracy -> Color(0x1AF59E0B)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, tint) = when (geofenceResult) {
                        is GeofenceValidationResult.Inside -> Pair(Icons.Default.GpsFixed, StatusSuccess)
                        is GeofenceValidationResult.Outside -> Pair(Icons.Default.LocationOn, StatusWarning)
                        is GeofenceValidationResult.PoorAccuracy -> Pair(Icons.Default.GpsNotFixed, StatusWarning)
                        is GeofenceValidationResult.MockLocationDetected -> Pair(Icons.Default.Warning, StatusError)
                        else -> Pair(Icons.Default.GpsOff, StatusError)
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val titleText = when (val res = geofenceResult) {
                            is GeofenceValidationResult.Inside -> "Campus Geofence Verified: ${res.geofence.name}"
                            is GeofenceValidationResult.Outside -> "Outside Campus Boundary (${res.distanceMeters.toInt()}m away)"
                            is GeofenceValidationResult.PoorAccuracy -> "GPS Accuracy Too Low (±${res.currentAccuracyMeters.toInt()}m)"
                            is GeofenceValidationResult.MockLocationDetected -> "Fake GPS Spoofing Blocked"
                            is GeofenceValidationResult.LocationDisabled -> "Location Services Disabled"
                            is GeofenceValidationResult.PermissionDenied -> "Location Permission Required"
                            GeofenceValidationResult.Loading -> "Acquiring High-Accuracy GPS Fix..."
                        }

                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = tint
                        )

                        val subText = when (val res = geofenceResult) {
                            is GeofenceValidationResult.Inside -> "Distance: ${res.distanceToCenterMeters.toInt()}m • Accuracy: ±${res.accuracyMeters.toInt()}m • Mock: None"
                            is GeofenceValidationResult.MockLocationDetected -> "Anti-spoofing engine rejected mock location"
                            else -> "Move inside institutional boundaries to enable attendance"
                        }

                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Camera Viewport & Face Guide Placeholder
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Face Oval Guide
                Box(
                    modifier = Modifier
                        .size(width = 220.dp, height = 280.dp)
                        .border(
                            width = 3.dp,
                            color = if (isInsideGeofence) SecondaryTeal else Color.Gray,
                            shape = RoundedCornerShape(110.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(90.dp)
                    )
                }

                // Biometric Status Notice
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isInsideGeofence) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isInsideGeofence) StatusSuccess else StatusWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isInsideGeofence) "Geofence Validated • Biometric Ready" else "Awaiting Campus Location Verification",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gated Action Trigger Button
            if (isInsideGeofence) {
                PrimaryGradientButton(
                    text = if (uiState.isCheckingIn) "Confirm Shift Check-In" else "Confirm Shift Check-Out",
                    icon = Icons.Default.Fingerprint,
                    onClick = {
                        if (uiState.isCheckingIn) {
                            viewModel.performCheckIn(onAttendanceSuccess)
                        } else {
                            viewModel.performCheckOut(onAttendanceSuccess)
                        }
                    }
                )
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = "Attendance is gated: You must be physically inside an active campus geofence to register shift check-in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
