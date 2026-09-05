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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.governence.faflow.domain.model.TimetableSlot
import com.governence.faflow.ui.components.DayOrderBadge
import com.governence.faflow.ui.components.FaflowEmptyState
import com.governence.faflow.ui.components.FaflowPillButton
import com.governence.faflow.ui.components.FaflowSectionHeader
import com.governence.faflow.ui.components.FaflowStatusBadge
import com.governence.faflow.ui.components.FaflowSurface
import com.governence.faflow.ui.components.PremiumTopBar
import com.governence.faflow.ui.theme.FaflowRoleColors
import com.governence.faflow.ui.theme.FaflowShapes
import com.governence.faflow.ui.theme.FaflowSpacing
import com.governence.faflow.ui.theme.FaflowStatusColors
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.viewmodels.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modern Teacher Productivity Dashboard for FAFLOW.
 * Clean, calm, minimal UI inspired by benchmark task management design.
 * Avoids card-overload by using generous whitespace, subtle surfaces, and hairline dividers.
 */
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToCheckIn: () -> Unit,
    onNavigateToTimetable: () -> Unit,
    onNavigateToClassTimetable: () -> Unit,
    onNavigateToTodayCoverage: () -> Unit,
    onNavigateToApplyLeave: () -> Unit,
    onNavigateToLeaveHistory: () -> Unit,
    onNavigateToCredits: () -> Unit,
    onNavigateToSubstitution: () -> Unit,
    onNavigateToAttendanceHistory: () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val todayDateFormatted = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())

    Scaffold(
        topBar = {
            PremiumTopBar(
                title = "FAFLOW",
                subtitle = todayDateFormatted,
                role = state.staff?.role ?: "Faculty",
                actions = {
                    IconButton(onClick = onNavigateToNotifications) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (state.isLoading && state.staff == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = FaflowSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(FaflowSpacing.lg),
                    contentPadding = PaddingValues(bottom = FaflowSpacing.xxxl)
                ) {
                    // Non-blocking Network / Server Connectivity Alert
                    if (state.isOfflineOrUnreachable || state.errorMessage != null) {
                        item {
                            FaflowSurface(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                contentPadding = PaddingValues(FaflowSpacing.md)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(FaflowSpacing.sm))
                                        Column {
                                            Text(
                                                text = "Unable to connect to FAFLOW server",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Check your network connection and try again.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(FaflowSpacing.sm))
                                    FaflowPillButton(
                                        text = "Retry",
                                        onClick = { viewModel.retry() },
                                        isPrimary = false
                                    )
                                }
                            }
                        }
                    }
                    // Header Greeting & Day Order
                    item {
                        Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Good morning,",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = state.staff?.name ?: "Faculty Member",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            DayOrderBadge(
                                dayOrder = state.todaySummary?.dayOrder,
                                isWorkingDay = !(state.todaySummary?.blocksOperations ?: false) && state.todaySummary?.dayOrder != null
                            )
                        }
                    }

                    // Primary Attendance Action Surface (Clean, Intentional)
                    item {
                        FaflowSurface(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            contentPadding = PaddingValues(FaflowSpacing.lg)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(PrimaryBlue.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Fingerprint,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(FaflowSpacing.md))
                                    Column {
                                        Text(
                                            text = "Attendance",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Verify location & identity",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                FaflowPillButton(
                                    text = "Check In",
                                    onClick = onNavigateToCheckIn,
                                    isPrimary = true
                                )
                            }
                        }
                    }

                    // Productivity Snapshot Metrics (Whitespace-focused row)
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FaflowSpacing.md)
                        ) {
                            FaflowSurface(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(FaflowSpacing.md),
                                onClick = onNavigateToCredits
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Leave Credits",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = FaflowStatusColors.Approved,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                                    Text(
                                        text = "${state.creditBalance}",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Available to apply",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            FaflowSurface(
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(FaflowSpacing.md),
                                onClick = onNavigateToTimetable
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Today's Schedule",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                                    Text(
                                        text = "${state.todaySlots.size} Periods",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (state.activeDutiesCount > 0) "+${state.activeDutiesCount} substitution" else "Regular roster",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Today's Timetable Section
                    item {
                        FaflowSectionHeader(
                            title = "Today's Schedule",
                            actionText = "View Full Week",
                            onActionClick = onNavigateToTimetable
                        )
                    }

                    if (state.todaySlots.isEmpty()) {
                        item {
                            FaflowSurface(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(FaflowSpacing.xl)
                            ) {
                                FaflowEmptyState(
                                    title = if (state.todaySummary?.blocksOperations == true) "Non-Working Day" else "No Classes Today",
                                    description = if (state.todaySummary?.blocksOperations == true) "Campus operations are paused for today." else "You have no scheduled teaching periods for today.",
                                    icon = Icons.Default.CalendarMonth
                                )
                            }
                        }
                    } else {
                        item {
                            FaflowSurface(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Column {
                                    state.todaySlots.forEachIndexed { index, slot ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onNavigateToTimetable() }
                                                .padding(FaflowSpacing.md),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(FaflowShapes.small)
                                                    .background(PrimaryBlue.copy(alpha = 0.08f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "P${slot.periodNumber}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryBlue
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(FaflowSpacing.md))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = slot.subjectName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "${slot.className} (${slot.section}) • Room ${slot.roomNumber}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        if (index < state.todaySlots.lastIndex) {
                                            HorizontalDivider(
                                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                thickness = 1.dp,
                                                modifier = Modifier.padding(start = 64.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Faculty Services Group (Unified Surface with Hairline Dividers)
                    item {
                        FaflowSectionHeader(title = "Faculty Services")
                    }

                    item {
                        FaflowSurface(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column {
                                FacultyServiceRow(
                                    title = "Apply for Leave",
                                    subtitle = "Request day or period leave with instant credit preview",
                                    icon = Icons.AutoMirrored.Filled.EventNote,
                                    iconColor = PrimaryBlue,
                                    onClick = onNavigateToApplyLeave
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(start = 56.dp)
                                )
                                FacultyServiceRow(
                                    title = "Substitutions & Duties",
                                    subtitle = "Review and accept coverage assignments",
                                    icon = Icons.Default.SwapHoriz,
                                    iconColor = FaflowStatusColors.Pending,
                                    onClick = onNavigateToSubstitution
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(start = 56.dp)
                                )
                                FacultyServiceRow(
                                    title = "Leave History",
                                    subtitle = "Track approvals, statuses, and ledger deductions",
                                    icon = Icons.Default.History,
                                    iconColor = FaflowStatusColors.Approved,
                                    onClick = onNavigateToLeaveHistory
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(start = 56.dp)
                                )
                                FacultyServiceRow(
                                    title = "Classwise Timetable",
                                    subtitle = "Explore student schedules across sections",
                                    icon = Icons.Default.CalendarMonth,
                                    iconColor = PrimaryBlue,
                                    onClick = onNavigateToClassTimetable
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
private fun FacultyServiceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = FaflowSpacing.md, vertical = FaflowSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(FaflowSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(12.dp)
        )
    }
}
