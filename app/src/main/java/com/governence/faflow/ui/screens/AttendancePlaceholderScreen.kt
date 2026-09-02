package com.governence.faflow.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
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
import com.governence.faflow.location.CampusGeofence
import com.governence.faflow.location.GeofenceType
import com.governence.faflow.location.GeofenceValidationResult
import com.governence.faflow.ui.components.AppTopBar
import com.governence.faflow.ui.components.PrimaryGradientButton
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.SecondaryTeal
import com.governence.faflow.ui.theme.StatusError
import com.governence.faflow.ui.theme.StatusInfo
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.theme.StatusWarning
import com.governence.faflow.ui.viewmodels.AttendanceViewModel

@Composable
fun AttendancePlaceholderScreen(
    viewModel: AttendanceViewModel,
    onNavigateToCheckIn: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val geofenceResult by viewModel.geofenceResult.collectAsState()
    val liveLocation by viewModel.liveLocation.collectAsState()
    val geofences by viewModel.geofences.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Palgeo Attendance",
                canNavigateBack = false
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Geofence & Location Radar Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (geofenceResult) {
                            is GeofenceValidationResult.Inside -> Color(0x1A10B981)
                            is GeofenceValidationResult.MockLocationDetected -> Color(0x1AEF4444)
                            is GeofenceValidationResult.PoorAccuracy -> Color(0x1AF59E0B)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val (icon, tint, title) = when (geofenceResult) {
                                is GeofenceValidationResult.Inside -> Triple(
                                    Icons.Default.GpsFixed,
                                    StatusSuccess,
                                    "Inside Campus Geofence"
                                )
                                is GeofenceValidationResult.Outside -> Triple(
                                    Icons.Default.LocationOn,
                                    StatusWarning,
                                    "Outside Campus Perimeter"
                                )
                                is GeofenceValidationResult.PoorAccuracy -> Triple(
                                    Icons.Default.GpsNotFixed,
                                    StatusWarning,
                                    "Poor GPS Signal Accuracy"
                                )
                                is GeofenceValidationResult.MockLocationDetected -> Triple(
                                    Icons.Default.Warning,
                                    StatusError,
                                    "Mock Location / Spoofing Detected"
                                )
                                is GeofenceValidationResult.LocationDisabled -> Triple(
                                    Icons.Default.GpsOff,
                                    StatusError,
                                    "GPS Location Disabled"
                                )
                                is GeofenceValidationResult.PermissionDenied -> Triple(
                                    Icons.Default.GpsOff,
                                    StatusError,
                                    "Location Permission Required"
                                )
                                GeofenceValidationResult.Loading -> Triple(
                                    Icons.Default.GpsFixed,
                                    PrimaryBlue,
                                    "Acquiring GPS Fix..."
                                )
                            }

                            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = tint
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val detailText = when (val res = geofenceResult) {
                            is GeofenceValidationResult.Inside ->
                                "Zone: ${res.geofence.name} • ${res.distanceToCenterMeters.toInt()}m from center (Accuracy: ±${res.accuracyMeters.toInt()}m)"
                            is GeofenceValidationResult.Outside ->
                                "Nearest: ${res.nearestGeofence?.name ?: "Campus"} (${res.distanceMeters.toInt()}m away)"
                            is GeofenceValidationResult.PoorAccuracy ->
                                "Accuracy is ±${res.currentAccuracyMeters.toInt()}m (Max allowed: ${res.requiredAccuracyMeters.toInt()}m). Move outside or near a window."
                            is GeofenceValidationResult.MockLocationDetected ->
                                "Anti-spoofing algorithm rejected fake GPS provider: ${res.provider}"
                            is GeofenceValidationResult.LocationDisabled ->
                                "Please enable Location Services (GPS) in your device settings."
                            is GeofenceValidationResult.PermissionDenied ->
                                "Grant Fine Location permission in Android Settings to verify campus attendance."
                            GeofenceValidationResult.Loading ->
                                "Listening for satellite signals and computing geodesic boundary..."
                        }

                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Shift Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val statusText = if (uiState.isShiftActive) "Shift Status: Checked In" else "Shift Status: Not Checked In"
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val shiftTimeText = if (uiState.isShiftActive && uiState.checkInTime != null) {
                            "Checked in today at ${uiState.checkInTime}"
                        } else {
                            "Morning Shift • 08:30 AM - 04:30 PM"
                        }

                        Text(
                            text = shiftTimeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        val isInside = geofenceResult is GeofenceValidationResult.Inside
                        PrimaryGradientButton(
                            text = if (uiState.isShiftActive) "Check Out" else "Check In (Face + Geofence)",
                            icon = Icons.Default.Fingerprint,
                            onClick = onNavigateToCheckIn
                        )
                    }
                }
            }

            // Active Campus Geofences Section (Circle & Polygon)
            item {
                Text(
                    text = "Active Institutional Geofences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(geofences) { geofence ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = PrimaryBlue)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = geofence.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val geoDesc = if (geofence.type == GeofenceType.CIRCLE) {
                                "Circular Boundary • Radius: ${geofence.radiusMeters.toInt()}m (±${geofence.toleranceMeters.toInt()}m)"
                            } else {
                                "Polygonal Boundary • ${geofence.polygonVertices.size} Coordinate Vertices"
                            }
                            Text(
                                text = geoDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(StatusSuccess.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusSuccess
                            )
                        }
                    }
                }
            }

            item {
                PrimaryGradientButton(
                    text = "View Monthly Attendance Ledger",
                    icon = Icons.Default.History,
                    onClick = onNavigateToHistory
                )
            }
        }
    }
}
