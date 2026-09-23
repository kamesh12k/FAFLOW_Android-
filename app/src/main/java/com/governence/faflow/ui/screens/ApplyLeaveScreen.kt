package com.governence.faflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.governence.faflow.core.network.RecommendationOutDto
import com.governence.faflow.ui.components.FaflowPillButton
import com.governence.faflow.ui.components.FaflowStatusBadge
import com.governence.faflow.ui.components.FaflowSurface
import com.governence.faflow.ui.theme.FaflowShapes
import com.governence.faflow.ui.theme.FaflowSpacing
import com.governence.faflow.ui.theme.FaflowStatusColors
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.StatusError
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.viewmodels.LeaveViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import com.governence.faflow.domain.model.InstitutionalSchedule

private val PERIOD_TIME_MAP = InstitutionalSchedule.PERIOD_TIMES

private val REASON_PRESETS = listOf(
    "Personal reason",
    "Medical leave",
    "Conference attendance",
    "Family event"
)

/**
 * Mobile-First Leave Application Screen for FAFLOW.
 * Reinterprets web features natively:
 * - Whole Day vs Custom Periods segmented toggle
 * - Tactile multi-select period chips with period times
 * - Academic calendar holiday resolution & operation block notice
 * - One-tap reason preset chips
 * - Batch leave dispatch
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApplyLeaveScreen(
    viewModel: LeaveViewModel,
    onNavigateBack: () -> Unit,
    onLeaveSubmitted: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var startDate by remember { mutableStateOf(todayStr) }
    var endDate by remember { mutableStateOf(todayStr) }
    var leaveType by remember { mutableStateOf("Casual Leave") }
    var isWholeDay by remember { mutableStateOf(true) }
    var selectedPeriods by remember { mutableStateOf(emptySet<Int>()) }
    var reason by remember { mutableStateOf("") }
    var attemptedSubmit by remember { mutableStateOf(false) }
    var expandedPeriod by remember { mutableStateOf<Int?>(null) }

    val isDateRangeInvalid = remember(startDate, endDate) {
        if (startDate.length == 10 && endDate.length == 10) {
            endDate < startDate
        } else false
    }

    val durationDays = remember(startDate, endDate) {
        try {
            if (startDate.length == 10 && endDate.length == 10 && startDate <= endDate) {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val s = sdf.parse(startDate)
                val e = sdf.parse(endDate)
                if (s != null && e != null) {
                    val cal = Calendar.getInstance()
                    cal.time = s
                    var workingDays = 0
                    while (!cal.time.after(e)) {
                        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                        if (dayOfWeek != Calendar.SUNDAY) {
                            workingDays++
                        }
                        cal.add(Calendar.DAY_OF_MONTH, 1)
                    }
                    if (workingDays > 0) workingDays else 1
                } else 1
            } else 1
        } catch (_: Exception) {
            1
        }
    }
    val isMultiDay = durationDays > 1

    LaunchedEffect(Unit) {
        viewModel.loadCampusMode()
        viewModel.loadTeacherTimetable()
    }

    LaunchedEffect(startDate) {
        if (startDate.length == 10) {
            viewModel.resolveDateDayOrder(startDate)
        }
    }

    LaunchedEffect(isWholeDay, state.isTimetableLoaded, state.scheduledPeriodsForDate) {
        if (isWholeDay) {
            if (state.isTimetableLoaded) {
                selectedPeriods = state.scheduledPeriodsForDate
            }
        }
    }

    LaunchedEffect(selectedPeriods) {
        if (selectedPeriods.size == 1) {
            expandedPeriod = selectedPeriods.first()
        } else if (expandedPeriod != null && !selectedPeriods.contains(expandedPeriod)) {
            expandedPeriod = selectedPeriods.firstOrNull()
        }
    }

    LaunchedEffect(startDate, selectedPeriods, state.isFlexibleMode) {
        if (state.isFlexibleMode && startDate.length == 10 && selectedPeriods.isNotEmpty()) {
            viewModel.loadCandidatesForPeriods(startDate, selectedPeriods)
        }
    }

    Scaffold(
        topBar = {
            com.governence.faflow.ui.components.AppTopBar(
                title = "Apply for Leave",
                subtitle = "Request single period or whole-day coverage",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        containerColor = com.governence.faflow.ui.theme.FaflowBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(com.governence.faflow.ui.theme.FaflowBg)
                .padding(horizontal = FaflowSpacing.lg)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(FaflowSpacing.sm))

            // Holiday or Operation Blocked Warning Banner
            if (state.isBlockedDate) {
                FaflowSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FaflowStatusColors.RejectedBg,
                    borderColor = StatusError.copy(alpha = 0.4f),
                    contentPadding = PaddingValues(FaflowSpacing.md)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(FaflowSpacing.sm))
                        Text(
                            text = "${startDate} is marked as a ${state.dayType ?: "Non-Working Day"}. Classes are not scheduled, so leave cannot be submitted.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusError
                        )
                    }
                }
                Spacer(modifier = Modifier.height(FaflowSpacing.md))
            } else if (state.hasExistingLeaveOnDate) {
                // Duplicate Leave Warning Banner
                FaflowSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = FaflowStatusColors.PendingBg,
                    borderColor = FaflowStatusColors.Pending.copy(alpha = 0.5f),
                    contentPadding = PaddingValues(FaflowSpacing.md)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = FaflowStatusColors.Pending,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(FaflowSpacing.sm))
                        Text(
                            text = "A leave request is already recorded for $startDate${if (state.existingLeavePeriods.isNotEmpty()) " (Periods: ${state.existingLeavePeriods.sorted().joinToString()})" else ""}. Please review your leave history before submitting again.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(FaflowSpacing.md))
            } else {
                // Standard Contextual Guidance
                FaflowSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color.White,
                    borderColor = com.governence.faflow.ui.theme.FaflowBorder,
                    contentPadding = PaddingValues(FaflowSpacing.md)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = com.governence.faflow.ui.theme.FaflowNavy,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(FaflowSpacing.sm))
                        Text(
                            text = "Leaves submitted close to slot time automatically trigger autonomous substitution candidate recommendations.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Spacer(modifier = Modifier.height(FaflowSpacing.md))
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError,
                    modifier = Modifier.padding(bottom = FaflowSpacing.sm)
                )
            }

            // Mode Selector: Whole Day vs Custom Periods
            Text(
                text = "Absence Duration",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FaflowShapes.pill)
                    .background(com.governence.faflow.ui.theme.FaflowDivider)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(FaflowShapes.pill)
                        .background(if (isWholeDay) com.governence.faflow.ui.theme.FaflowNavy else Color.Transparent)
                        .clickable {
                            isWholeDay = true
                            selectedPeriods = if (state.isTimetableLoaded) {
                                state.scheduledPeriodsForDate
                            } else {
                                emptySet()
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Whole Day",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isWholeDay) FontWeight.Bold else FontWeight.Medium,
                        color = if (isWholeDay) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(FaflowShapes.pill)
                        .background(if (!isWholeDay) com.governence.faflow.ui.theme.FaflowNavy else Color.Transparent)
                        .clickable {
                            isWholeDay = false
                            if (selectedPeriods.isEmpty()) {
                                selectedPeriods = if (state.scheduledPeriodsForDate.isNotEmpty()) {
                                    state.scheduledPeriodsForDate
                                } else {
                                    setOf(1)
                                }
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Custom Periods",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (!isWholeDay) FontWeight.Bold else FontWeight.Medium,
                        color = if (!isWholeDay) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.md))

            // Leave Type Selector
            Text(
                text = "Leave Type",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Casual Leave", "On Duty", "Medical Leave").forEach { type ->
                    val isSelected = leaveType == type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(FaflowShapes.pill)
                            .background(if (isSelected) com.governence.faflow.ui.theme.FaflowNavy else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color.Transparent else com.governence.faflow.ui.theme.FaflowBorder,
                                shape = FaflowShapes.pill
                            )
                            .clickable { leaveType = type }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.md))

            // Start Date and End Date Fields
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Start Date (YYYY-MM-DD)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = {
                            startDate = it
                            if (endDate < it) {
                                endDate = it
                            }
                        },
                        singleLine = true,
                        shape = FaflowShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = com.governence.faflow.ui.theme.FaflowBorder,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "End Date (YYYY-MM-DD)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        singleLine = true,
                        isError = isDateRangeInvalid,
                        shape = FaflowShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = if (isDateRangeInvalid) StatusError else com.governence.faflow.ui.theme.FaflowBorder,
                            errorBorderColor = StatusError,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (isDateRangeInvalid) {
                Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                Text(
                    text = "End date cannot be earlier than start date",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusError
                )
            } else if (durationDays > 1) {
                Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FaflowStatusBadge(
                        text = "$durationDays Days Duration",
                        statusColor = PrimaryBlue,
                        showDot = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.sm))

            // Resolved Day Order Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calendar Day Order",
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

            // Period Selector Chips
            val headerText = if (isWholeDay) {
                if (state.isTimetableLoaded && state.teacherSlots.isNotEmpty()) {
                    if (state.scheduledPeriodsForDate.isNotEmpty()) {
                        "Scheduled Classes (${state.scheduledPeriodsForDate.size} ${if (state.scheduledPeriodsForDate.size == 1) "Period" else "Periods"})"
                    } else {
                        "No Scheduled Classes on this Day"
                    }
                } else {
                    "All Periods Included"
                }
            } else {
                "Select Absence Periods"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isWholeDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.governence.faflow.ui.theme.PrimaryBlue,
                            modifier = Modifier.clickable { selectedPeriods = setOf(1, 2, 3, 4, 5) }
                        )
                        Text(
                            text = "Clear All",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = com.governence.faflow.ui.theme.StatusError,
                            modifier = Modifier.clickable { selectedPeriods = emptySet() }
                        )
                    }
                }
            }

            if (isWholeDay && state.isTimetableLoaded && state.teacherSlots.isNotEmpty() && state.scheduledPeriodsForDate.isEmpty()) {
                Spacer(modifier = Modifier.height(FaflowSpacing.xs))
                Text(
                    text = "You have no classes scheduled on Day Order ${state.resolvedDayOrder ?: ""}. No substitution coverage needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (1..5).forEach { period ->
                    val isScheduled = state.scheduledPeriodsForDate.contains(period)
                    val isSelected = selectedPeriods.contains(period)
                    val bg = if (isSelected) com.governence.faflow.ui.theme.FaflowNavy else Color.White
                    val fg = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    val border = if (isSelected) Color.Transparent else com.governence.faflow.ui.theme.FaflowBorder

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(FaflowShapes.medium)
                            .background(bg)
                            .border(width = 1.dp, color = border, shape = FaflowShapes.medium)
                            .clickable(enabled = !isWholeDay) {
                                selectedPeriods = if (selectedPeriods.contains(period)) {
                                    selectedPeriods - period
                                } else {
                                    selectedPeriods + period
                                }
                            }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "P$period",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = fg
                        )
                        Text(
                            text = PERIOD_TIME_MAP[period] ?: "",
                            fontSize = 8.sp,
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        if (state.isTimetableLoaded && state.teacherSlots.isNotEmpty()) {
                            Text(
                                text = if (isScheduled) "Class" else "Free",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) {
                                    if (isScheduled) Color(0xFF93C5FD) else Color.White.copy(alpha = 0.6f)
                                } else {
                                    if (isScheduled) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.lg))

            // ---- Flexible Mode: Proposed Substitute Selection ----
            if (state.isFlexibleMode && selectedPeriods.isNotEmpty()) {
                val coveredCount = selectedPeriods.count { state.selectedSubstitutePerPeriod.containsKey(it) }
                val isFullyCovered = coveredCount == selectedPeriods.size

                FaflowSurface(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = if (isFullyCovered) StatusSuccess.copy(alpha = 0.08f) else PrimaryBlue.copy(alpha = 0.06f),
                    borderColor = if (isFullyCovered) StatusSuccess.copy(alpha = 0.4f) else PrimaryBlue.copy(alpha = 0.3f),
                    contentPadding = PaddingValues(FaflowSpacing.md)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isFullyCovered) Icons.Default.Check else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (isFullyCovered) StatusSuccess else PrimaryBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Flexible Mode · Substitute Nomination",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFullyCovered) StatusSuccess else PrimaryBlue
                                )
                            }
                            FaflowStatusBadge(
                                text = "$coveredCount/${selectedPeriods.size} nominated",
                                statusColor = if (isFullyCovered) StatusSuccess else FaflowStatusColors.Pending
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Please nominate an available faculty substitute for each scheduled class. Use the filters below to widen or narrow faculty eligibility.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FaflowSpacing.sm))

                // ---- Filter Controls & Real-Time Search Bar ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FaflowShapes.medium)
                        .background(Color.White)
                        .border(1.dp, com.governence.faflow.ui.theme.FaflowBorder, FaflowShapes.medium)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cross Department Filter Chip
                        val isCross = state.includeCrossDepartment
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(FaflowShapes.pill)
                                .background(if (isCross) com.governence.faflow.ui.theme.FaflowNavy else Color(0xFFF1F5F9))
                                .border(
                                    1.dp,
                                    if (isCross) com.governence.faflow.ui.theme.FaflowNavy else Color(0xFFCBD5E1),
                                    FaflowShapes.pill
                                )
                                .clickable { viewModel.toggleCrossDepartment(startDate, selectedPeriods) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Business,
                                    contentDescription = null,
                                    tint = if (isCross) Color.White else Color(0xFF475569),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Cross Dept",
                                    fontSize = 11.sp,
                                    fontWeight = if (isCross) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCross) Color.White else Color(0xFF334155)
                                )
                            }
                        }

                        // Same Class Faculty Filter Chip ("Handles This Class")
                        val isClassOnly = state.onlyHandlesClass
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(FaflowShapes.pill)
                                .background(if (isClassOnly) com.governence.faflow.ui.theme.FaflowNavy else Color(0xFFF1F5F9))
                                .border(
                                    1.dp,
                                    if (isClassOnly) com.governence.faflow.ui.theme.FaflowNavy else Color(0xFFCBD5E1),
                                    FaflowShapes.pill
                                )
                                .clickable { viewModel.toggleOnlyHandlesClass(startDate, selectedPeriods) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = if (isClassOnly) Color.White else Color(0xFF475569),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Same Class",
                                    fontSize = 11.sp,
                                    fontWeight = if (isClassOnly) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isClassOnly) Color.White else Color(0xFF334155)
                                )
                            }
                        }
                    }

                    // Real-Time Search Field
                    OutlinedTextField(
                        value = state.candidateSearchQuery,
                        onValueChange = { viewModel.setCandidateSearchQuery(it) },
                        placeholder = { Text("Filter faculty by name or dept…", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (state.candidateSearchQuery.isNotBlank()) {
                                IconButton(onClick = { viewModel.setCandidateSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = FaflowShapes.pill,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(FaflowSpacing.sm))

                if (state.isLoadingCandidates && state.candidatesPerPeriod.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = FaflowSpacing.md),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Finding eligible substitute candidates…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        selectedPeriods.sorted().forEach { period ->
                            val periodCandidates = state.candidatesPerPeriod[period] ?: emptyList()
                            val pickedId = state.selectedSubstitutePerPeriod[period]
                            val pickedCandidate = periodCandidates.firstOrNull { it.id == pickedId }
                            val isExpanded = expandedPeriod == period || selectedPeriods.size == 1

                            // Filter candidates locally by search query
                            val query = state.candidateSearchQuery.trim()
                            val matchingCandidates = if (query.isBlank()) {
                                periodCandidates
                            } else {
                                periodCandidates.filter {
                                    it.teacherName.contains(query, ignoreCase = true) ||
                                            it.department?.contains(query, ignoreCase = true) == true
                                }
                            }

                            FaflowSurface(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = Color.White,
                                borderColor = if (pickedId != null) StatusSuccess.copy(alpha = 0.5f) else com.governence.faflow.ui.theme.FaflowBorder,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Column {
                                    // Row Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedPeriod = if (expandedPeriod == period) null else period
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(FaflowShapes.small)
                                                    .background(com.governence.faflow.ui.theme.FaflowNavy)
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "P$period",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = PERIOD_TIME_MAP[period] ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (pickedCandidate != null) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(FaflowShapes.pill)
                                                        .background(Color(0xFFECFDF5))
                                                        .border(1.dp, Color(0xFFA7F3D0), FaflowShapes.pill)
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .clip(FaflowShapes.pill)
                                                                .background(Color(0xFF10B981))
                                                        )
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        Text(
                                                            text = pickedCandidate.teacherName,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF065F46),
                                                            maxLines = 1
                                                        )
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        Text(
                                                            text = "×",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF059669),
                                                            modifier = Modifier.clickable {
                                                                viewModel.clearSubstituteForPeriod(period)
                                                            }
                                                        )
                                                    }
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(FaflowShapes.pill)
                                                        .background(Color(0xFFFFFBEB))
                                                        .border(1.dp, Color(0xFFFDE68A), FaflowShapes.pill)
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "Select Sub",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF92400E)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Expandable Candidates Drawer
                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Drawer Sub-Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "RECOMMENDED FACULTY (${matchingCandidates.size})",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF475569),
                                                letterSpacing = 0.5.sp
                                            )
                                            if (pickedId != null) {
                                                Text(
                                                    text = "Clear Selection",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFE11D48),
                                                    modifier = Modifier.clickable {
                                                        viewModel.clearSubstituteForPeriod(period)
                                                    }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (matchingCandidates.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color(0xFFFFFBEB))
                                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(10.dp))
                                                    .padding(10.dp)
                                            ) {
                                                Text(
                                                    text = if (state.isLoadingCandidates)
                                                        "Loading candidates for Period $period…"
                                                    else if (query.isNotBlank())
                                                        "No faculty matching \"$query\" found for Period P$period."
                                                    else
                                                        "No free faculty found for Period P$period. Try enabling Cross-Department.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF92400E)
                                                )
                                            }
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                matchingCandidates.forEach { candidate ->
                                                    val isPicked = candidate.id == pickedId
                                                    RecommendationCard(
                                                        candidate = candidate,
                                                        isSelected = isPicked,
                                                        onSelect = {
                                                            if (isPicked) {
                                                                viewModel.clearSubstituteForPeriod(period)
                                                            } else {
                                                                viewModel.selectSubstituteForPeriod(period, candidate.id)
                                                                expandedPeriod = null // close tray on nominate
                                                            }
                                                        },
                                                        periodNumber = period
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(FaflowSpacing.lg))
            }

            // Reason Presets
            Text(
                text = "Reason Presets",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                REASON_PRESETS.forEach { preset ->
                    val isPicked = reason == preset
                    Box(
                        modifier = Modifier
                            .clip(FaflowShapes.pill)
                            .background(if (isPicked) com.governence.faflow.ui.theme.FaflowNavy.copy(alpha = 0.12f) else Color.White)
                            .border(
                                width = 1.dp,
                                color = if (isPicked) com.governence.faflow.ui.theme.FaflowNavy else com.governence.faflow.ui.theme.FaflowBorder,
                                shape = FaflowShapes.pill
                            )
                            .clickable { reason = preset }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPicked) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = com.governence.faflow.ui.theme.FaflowNavy,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Normal,
                                color = if (isPicked) com.governence.faflow.ui.theme.FaflowNavy else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.md))

            // Detailed Reason Input
            Text(
                text = "Detailed Explanation",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(FaflowSpacing.xs))
            OutlinedTextField(
                value = reason,
                onValueChange = {
                    reason = it
                    if (it.isNotBlank()) attemptedSubmit = false
                },
                placeholder = { Text("Specify additional details for absence…", style = MaterialTheme.typography.bodySmall) },
                minLines = 3,
                maxLines = 5,
                isError = attemptedSubmit && reason.trim().isEmpty(),
                shape = FaflowShapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = if (attemptedSubmit && reason.trim().isEmpty()) StatusError else com.governence.faflow.ui.theme.FaflowBorder,
                    errorBorderColor = StatusError,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (attemptedSubmit && reason.trim().isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason is required",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusError
                )
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.xxl))

            // Submit Button
            val hasConflict = state.hasExistingLeaveOnDate && (isWholeDay || selectedPeriods.any { it in state.existingLeavePeriods })
            val isFlexibleCoverageOk = !state.isFlexibleMode || selectedPeriods.all { p ->
                state.selectedSubstitutePerPeriod.containsKey(p)
            }
            val isSubmitEnabled = !isDateRangeInvalid && selectedPeriods.isNotEmpty() &&
                    !state.isBlockedDate && !hasConflict && isFlexibleCoverageOk && (!attemptedSubmit || reason.isNotBlank())

            if (state.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                val buttonText = when {
                    isDateRangeInvalid ->
                        "Invalid Date Range"
                    state.isTimetableLoaded && state.teacherSlots.isNotEmpty() && state.scheduledPeriodsForDate.isEmpty() && isWholeDay && !isMultiDay ->
                        "No Scheduled Classes to Cover"
                    selectedPeriods.isEmpty() ->
                        "Select Absence Period"
                    isMultiDay ->
                        "Submit $leaveType ($durationDays Days)"
                    isWholeDay && selectedPeriods.size == 1 ->
                        "Submit $leaveType (1 Period Covered)"
                    isWholeDay ->
                        "Submit Whole Day $leaveType (${selectedPeriods.size} Periods)"
                    selectedPeriods.size == 1 ->
                        "Submit $leaveType (Period ${selectedPeriods.first()})"
                    else ->
                        "Submit $leaveType (${selectedPeriods.size} Periods)"
                }

                FaflowPillButton(
                    text = buttonText,
                    onClick = {
                        if (reason.trim().isEmpty()) {
                            attemptedSubmit = true
                            return@FaflowPillButton
                        }
                        if (isDateRangeInvalid) {
                            return@FaflowPillButton
                        }
                        val periodsList = selectedPeriods.toList().sorted()
                        val periodSubstitutes = if (state.isFlexibleMode) {
                            state.selectedSubstitutePerPeriod
                                .mapKeys { it.key.toString() }
                                .ifEmpty { null }
                        } else null

                        viewModel.submitLeaveRange(
                            startDate = startDate,
                            endDate = endDate,
                            leaveType = leaveType,
                            reason = reason,
                            selectedPeriods = periodsList,
                            isWholeDay = isWholeDay || isMultiDay,
                            onComplete = onLeaveSubmitted,
                            periodSubstitutes = periodSubstitutes
                        )
                    },
                    enabled = isSubmitEnabled,
                    isPrimary = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.xxxl))
        }
    }
}

/**
 * Authoritative ranked candidate recommendation card for Flexible Mode.
 * Mirrors the exact layout and visual design from the web ApplyLeave component:
 * - Avatar with monogram and vibrant indigo/emerald gradient
 * - Faculty name and department badge
 * - Calibrated suitability tier badge and score progress bar (score out of 100)
 * - Workload simulation 3-column grid (Total Today, Back-to-Back, Weekly Total)
 * - Contextual reason badges (warning, subject, department, workload continuity)
 * - Tactile Nominate / Assign button
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecommendationCard(
    candidate: RecommendationOutDto,
    isSelected: Boolean,
    onSelect: () -> Unit,
    periodNumber: Int
) {
    val teacherName = candidate.teacherName
    val deptName = candidate.department
    val initial = (teacherName.firstOrNull() ?: 'T').uppercase()
    val score = candidate.normalizedScore
    val tier = candidate.suitabilityTier
    val todayLoad = candidate.resolvedTodayLoad
    val projToday = candidate.resolvedProjToday
    val weekLoad = candidate.resolvedWeekLoad
    val projWeek = candidate.resolvedProjWeek
    val contLoad = candidate.resolvedContLoad
    val projCont = candidate.resolvedProjCont
    val hasContinuousWarning = candidate.hasContinuousWarning
    val todayPeriods = candidate.todayPeriods
    val subsWeek = candidate.substitutionsWeek ?: 0

    val tierBgColor = when (tier) {
        "EXCELLENT" -> Color(0xFFD1FAE5)
        "GOOD" -> Color(0xFFDBEAFE)
        "FAIR" -> Color(0xFFFEF3C7)
        else -> Color(0xFFF1F5F9)
    }
    val tierTextColor = when (tier) {
        "EXCELLENT" -> Color(0xFF065F46)
        "GOOD" -> Color(0xFF1E40AF)
        "FAIR" -> Color(0xFF92400E)
        else -> Color(0xFF475569)
    }
    val tierBorderColor = when (tier) {
        "EXCELLENT" -> Color(0xFF6EE7B7)
        "GOOD" -> Color(0xFF93C5FD)
        "FAIR" -> Color(0xFFFCD34D)
        else -> Color(0xFFCBD5E1)
    }

    val cardBorderColor = if (isSelected) Color(0xFF34D399) else Color(0xFFE2E8F0)
    val cardBgColor = if (isSelected) Color(0xFFECFDF5).copy(alpha = 0.5f) else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = cardBorderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Avatar, Name, Dept, Suitability Bar & Tier, Assign Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar Box with Gradient
                    val avatarGradient = if (isSelected) {
                        Brush.linearGradient(listOf(Color(0xFF059669), Color(0xFF047857)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF3730A3)))
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(avatarGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = teacherName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                fontSize = 13.sp
                            )
                            if (!deptName.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF1F5F9))
                                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = deptName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Score progress bar + Tier + Suitability Score
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Progress bar
                            val progressColor = if (score >= 75) Color(0xFF10B981) else if (score >= 45) Color(0xFFF59E0B) else Color(0xFF94A3B8)
                            Box(
                                modifier = Modifier
                                    .width(52.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(0xFFF1F5F9))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(score / 100f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(progressColor)
                                )
                            }

                            // Tier badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(tierBgColor)
                                    .border(1.dp, tierBorderColor, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = tier,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = tierTextColor,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            // Suitability pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFF8FAFC))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "Suitability: $score/100",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF334155)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Assign / Nominate Button
                val buttonBg = if (isSelected) Color(0xFF059669) else PrimaryBlue
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(buttonBg)
                        .clickable { onSelect() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isSelected) "✓ Nominated" else "Assign",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Workload Simulation 3-Column Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Column 1: Total Today
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "TOTAL TODAY",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (todayLoad == 0) "Free (0 classes)" else "$todayLoad → $projToday classes",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        if (todayPeriods.isNotEmpty()) {
                            Text(
                                text = "Periods: P${todayPeriods.sorted().joinToString(", P")}",
                                fontSize = 8.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1
                            )
                        } else if (todayLoad == 0) {
                            Text(
                                text = "Free all day",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF059669)
                            )
                        } else {
                            Text(
                                text = "$todayLoad periods total",
                                fontSize = 8.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(Color(0xFFE2E8F0))
                    )

                    // Column 2: Back-to-Back
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "BACK-TO-BACK",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$contLoad → $projCont in a row",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (hasContinuousWarning) Color(0xFFB45309) else Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (hasContinuousWarning) "⚠️ $projCont in a row" else if (projCont > 1) "Classes in a row" else "No consecutive",
                            fontSize = 8.sp,
                            fontWeight = if (hasContinuousWarning) FontWeight.Bold else FontWeight.Normal,
                            color = if (hasContinuousWarning) Color(0xFFB45309) else Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(Color(0xFFE2E8F0))
                    )

                    // Column 3: Weekly Total
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "WEEKLY TOTAL",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$weekLoad → $projWeek classes",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$subsWeek sub(s) this week",
                            fontSize = 8.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Distinct Reason Badges
            if (candidate.reasons.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    candidate.reasons.forEach { r ->
                        val isWarn = r.contains("⚠") || r.contains("Fatigue", ignoreCase = true) || r.contains("break", ignoreCase = true)
                        val isSubject = r.contains("subject", ignoreCase = true)
                        val isDept = r.contains("department", ignoreCase = true)

                        val badgeBg = when {
                            isWarn -> Color(0xFFFEF3C7)
                            isSubject -> Color(0xFFEFF6FF)
                            isDept -> Color(0xFFEEF2FF)
                            else -> Color(0xFFF1F5F9)
                        }
                        val badgeText = when {
                            isWarn -> Color(0xFF92400E)
                            isSubject -> Color(0xFF1D4ED8)
                            isDept -> Color(0xFF4338CA)
                            else -> Color(0xFF475569)
                        }
                        val badgeBorder = when {
                            isWarn -> Color(0xFFFDE68A)
                            isSubject -> Color(0xFFBFDBFE)
                            isDept -> Color(0xFFC7D2FE)
                            else -> Color(0xFFE2E8F0)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .border(1.dp, badgeBorder, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = r,
                                fontSize = 9.sp,
                                fontWeight = if (isWarn) FontWeight.Bold else FontWeight.SemiBold,
                                color = badgeText
                            )
                        }
                    }
                }
            }
        }
    }
}
