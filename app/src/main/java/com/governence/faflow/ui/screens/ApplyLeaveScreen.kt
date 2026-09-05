package com.governence.faflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.governence.faflow.ui.components.FaflowPillButton
import com.governence.faflow.ui.components.FaflowStatusBadge
import com.governence.faflow.ui.components.FaflowSurface
import com.governence.faflow.ui.theme.FaflowShapes
import com.governence.faflow.ui.theme.FaflowSpacing
import com.governence.faflow.ui.theme.FaflowStatusColors
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.StatusError
import com.governence.faflow.ui.theme.StatusWarning
import com.governence.faflow.ui.viewmodels.LeaveViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern Leave Application Screen for FAFLOW.
 * Clean, intelligent form with tactile period chips, automatic Day Order resolution,
 * and contextual substitution routing notices.
 */
@Composable
fun ApplyLeaveScreen(
    viewModel: LeaveViewModel,
    onNavigateBack: () -> Unit,
    onLeaveSubmitted: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var leaveDate by remember { mutableStateOf(todayStr) }
    var selectedPeriod by remember { mutableIntStateOf(1) }
    var reason by remember { mutableStateOf("") }

    LaunchedEffect(leaveDate) {
        if (leaveDate.length == 10) {
            viewModel.resolveDateDayOrder(leaveDate)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FaflowSpacing.md, vertical = FaflowSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(FaflowSpacing.xs))
                Column {
                    Text(
                        text = "Apply for Leave",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Submit period or day absence request",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                .padding(horizontal = FaflowSpacing.lg)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(FaflowSpacing.sm))

            // Contextual Guidance Surface (Replaces bulky card)
            FaflowSurface(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = FaflowStatusColors.PendingBg,
                borderColor = FaflowStatusColors.Pending.copy(alpha = 0.3f),
                contentPadding = PaddingValues(FaflowSpacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = FaflowStatusColors.Pending,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(FaflowSpacing.sm))
                    Text(
                        text = "Requests submitted within 2 hours of slot time will trigger automatic emergency substitution routing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.lg))

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError,
                    modifier = Modifier.padding(bottom = FaflowSpacing.sm)
                )
            }

            // Date Field
            Text(
                text = "Leave Date",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
            OutlinedTextField(
                value = leaveDate,
                onValueChange = { leaveDate = it },
                singleLine = true,
                shape = FaflowShapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(FaflowSpacing.md))

            // Resolved Day Order Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendar Schedule",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state.resolvedDayOrder != null) {
                    FaflowStatusBadge(
                        text = "Day Order ${state.resolvedDayOrder}",
                        statusColor = PrimaryBlue,
                        showDot = true
                    )
                } else {
                    Text(
                        text = "Resolving day order…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.lg))

            // Period Selector Chips (Direct tap instead of numeric text field)
            Text(
                text = "Teaching Period",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FaflowSpacing.sm)
            ) {
                (1..5).forEach { period ->
                    val isSelected = selectedPeriod == period
                    val bg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val fg = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    val border = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(FaflowShapes.pill)
                            .background(bg)
                            .border(width = 1.dp, color = border, shape = FaflowShapes.pill)
                            .clickable { selectedPeriod = period }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "P$period",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = fg
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.lg))

            // Reason Text Field
            Text(
                text = "Reason for Absence",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text("State the reason for this absence…", style = MaterialTheme.typography.bodySmall) },
                minLines = 3,
                maxLines = 5,
                shape = FaflowShapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(FaflowSpacing.xxl))

            // Submit Button
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                FaflowPillButton(
                    text = "Submit Leave Request",
                    onClick = {
                        if (reason.isNotBlank()) {
                            viewModel.submitLeave(leaveDate, selectedPeriod, reason, onLeaveSubmitted)
                        }
                    },
                    enabled = reason.isNotBlank(),
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.xxxl))
        }
    }
}
