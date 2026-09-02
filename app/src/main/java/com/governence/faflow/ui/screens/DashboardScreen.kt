package com.governence.faflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.governence.faflow.ui.components.AppTopBar
import com.governence.faflow.ui.components.PrimaryGradientButton
import com.governence.faflow.ui.components.StatCard
import com.governence.faflow.ui.theme.CardHighlight
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.SecondaryTeal
import com.governence.faflow.ui.theme.StatusInfo
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.theme.StatusWarning

@Composable
fun DashboardScreen(
    onNavigateToStartAttendance: () -> Unit,
    onNavigateToPersonList: () -> Unit,
    onNavigateToAttendanceHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSyncStatus: () -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Facial Attendance",
                canNavigateBack = false,
                actions = {
                    IconButton(onClick = onNavigateToSyncStatus) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
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
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "READY FOR ATTENDANCE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Launch Live Facial Attendance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scan students with multi-face detection, ArcFace embedding extraction, and real-time anti-spoofing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PrimaryGradientButton(
                            text = "Start Session",
                            icon = Icons.Default.CameraAlt,
                            onClick = onNavigateToStartAttendance
                        )
                    }
                }
            }

            item {
                Text(
                    text = "System Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                StatCard(
                    title = "Enrolled Persons",
                    value = "48 / 50",
                    subtitle = "96% Biometric enrollment rate",
                    icon = Icons.Default.Group,
                    iconTint = PrimaryBlue,
                    iconBackground = CardHighlight,
                    onClick = onNavigateToPersonList
                )
            }

            item {
                StatCard(
                    title = "Attendance Today",
                    value = "142",
                    subtitle = "Across 4 active sessions",
                    icon = Icons.Default.CheckCircle,
                    iconTint = StatusSuccess,
                    iconBackground = Color(0x1A10B981),
                    onClick = onNavigateToAttendanceHistory
                )
            }

            item {
                StatCard(
                    title = "Sync & Connectivity",
                    value = "Online",
                    subtitle = "0 pending offline sync records",
                    icon = Icons.Default.Sync,
                    iconTint = StatusInfo,
                    iconBackground = Color(0x1A3B82F6),
                    onClick = onNavigateToSyncStatus
                )
            }
        }
    }
}
