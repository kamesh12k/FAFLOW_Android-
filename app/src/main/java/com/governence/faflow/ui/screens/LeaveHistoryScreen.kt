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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.governence.faflow.domain.model.LeaveRequest
import com.governence.faflow.domain.model.LeaveStatus
import com.governence.faflow.ui.components.AppTopBar
import com.governence.faflow.ui.components.EmptyStateView
import com.governence.faflow.ui.components.ErrorRetryView
import com.governence.faflow.ui.components.FaflowStatusBadge
import com.governence.faflow.ui.components.FaflowSurface
import com.governence.faflow.ui.theme.FaflowSpacing
import com.governence.faflow.ui.theme.StatusError
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.theme.StatusWarning
import com.governence.faflow.ui.viewmodels.LeaveViewModel

@Composable
fun LeaveHistoryScreen(
    viewModel: LeaveViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var leaveToCancel by remember { mutableStateOf<LeaveRequest?>(null) }

    if (leaveToCancel != null) {
        AlertDialog(
            onDismissRequest = { leaveToCancel = null },
            title = { Text("Cancel Leave Request") },
            text = { Text("Are you sure you want to cancel your leave request for ${leaveToCancel?.date} (Period ${leaveToCancel?.periodNumber})?") },
            confirmButton = {
                Button(
                    onClick = {
                        leaveToCancel?.let { viewModel.cancelLeave(it.id) }
                        leaveToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusError)
                ) {
                    Text("Confirm Cancel")
                }
            },
            dismissButton = {
                TextButton(onClick = { leaveToCancel = null }) {
                    Text("Keep Leave")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Leave Request History",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        } else if (state.errorMessage != null && state.myLeaves.isEmpty()) {
            ErrorRetryView(
                message = state.errorMessage!!,
                onRetry = { viewModel.loadMyLeaves() },
                modifier = Modifier.padding(innerPadding)
            )
        } else if (state.myLeaves.isEmpty()) {
            EmptyStateView(
                title = "No Leave History",
                description = "You have not submitted any faculty leave requests yet.",
                icon = Icons.Default.EventBusy,
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = FaflowSpacing.lg),
                contentPadding = PaddingValues(top = FaflowSpacing.md, bottom = FaflowSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(FaflowSpacing.md)
            ) {
                items(state.myLeaves) { leave ->
                    FaflowSurface(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = MaterialTheme.colorScheme.surface,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        contentPadding = PaddingValues(FaflowSpacing.md)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${leave.date} • Period ${leave.periodNumber} (Day Order ${leave.dayOrder})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    LeaveStatusBadge(status = leave.status)
                                    if (leave.status == LeaveStatus.PENDING) {
                                        IconButton(onClick = { leaveToCancel = leave }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Cancel Leave",
                                                tint = StatusError
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(FaflowSpacing.xs))

                            Text(
                                text = "Reason: ${leave.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (leave.substituteTeacherName != null) {
                                Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                                Text(
                                    text = "Covered By: ${leave.substituteTeacherName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaveStatusBadge(status: LeaveStatus) {
    val (text, color) = when (status) {
        LeaveStatus.APPROVED -> Pair("APPROVED", StatusSuccess)
        LeaveStatus.PENDING -> Pair("PENDING", StatusWarning)
        LeaveStatus.REJECTED -> Pair("REJECTED", StatusError)
        LeaveStatus.CANCELLED -> Pair("CANCELLED", Color.Gray)
    }

    FaflowStatusBadge(text = text, statusColor = color)
}
