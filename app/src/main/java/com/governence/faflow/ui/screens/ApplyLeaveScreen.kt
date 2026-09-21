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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.governence.faflow.core.network.SlotCandidateOutDto
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import java.text.SimpleDateFormat
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
    var leaveDate by remember { mutableStateOf(todayStr) }
    var isWholeDay by remember { mutableStateOf(true) }
    var selectedPeriods by remember { mutableStateOf(emptySet<Int>()) }
    var reason by remember { mutableStateOf("") }
    var expandedPeriod by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadCampusMode()
        viewModel.loadTeacherTimetable()
    }

    LaunchedEffect(leaveDate) {
        if (leaveDate.length == 10) {
            viewModel.resolveDateDayOrder(leaveDate)
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

    LaunchedEffect(leaveDate, selectedPeriods, state.isFlexibleMode) {
        if (state.isFlexibleMode && leaveDate.length == 10 && selectedPeriods.isNotEmpty()) {
            viewModel.loadCandidatesForPeriods(leaveDate, selectedPeriods)
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
                            text = "${leaveDate} is marked as a ${state.dayType ?: "Non-Working Day"}. Classes are not scheduled, so leave cannot be submitted.",
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
                            text = "A leave request is already recorded for $leaveDate${if (state.existingLeavePeriods.isNotEmpty()) " (Periods: ${state.existingLeavePeriods.sorted().joinToString()})" else ""}. Please review your leave history before submitting again.",
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

            // Leave Date Field
            Text(
                text = "Leave Date (YYYY-MM-DD)",
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
                    unfocusedBorderColor = com.governence.faflow.ui.theme.FaflowBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

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
                            text = "Please nominate an available faculty substitute for each period. Your HOD will officially confirm the assignments.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedPeriods.sorted().forEach { period ->
                            val periodCandidates = state.candidatesPerPeriod[period] ?: emptyList()
                            val pickedId = state.selectedSubstitutePerPeriod[period]
                            val pickedCandidate = periodCandidates.firstOrNull { it.id == pickedId }
                            val isExpanded = expandedPeriod == period || selectedPeriods.size == 1

                            FaflowSurface(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = Color.White,
                                borderColor = if (pickedId != null) StatusSuccess.copy(alpha = 0.5f) else com.governence.faflow.ui.theme.FaflowBorder,
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Column {
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
                                                Text(
                                                    text = pickedCandidate.name,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = StatusSuccess,
                                                    maxLines = 1
                                                )
                                            } else {
                                                Text(
                                                    text = "Select Substitute",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = FaflowStatusColors.Pending
                                                )
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

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        if (periodCandidates.isEmpty()) {
                                            Text(
                                                text = if (state.isLoadingCandidates) "Loading candidates for Period $period…" else "No available substitutes found for Period $period.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                periodCandidates.take(5).forEach { candidate ->
                                                    CandidatePickerRow(
                                                        candidate = candidate,
                                                        isSelected = candidate.id == pickedId,
                                                        onSelect = {
                                                            if (candidate.id == pickedId) {
                                                                viewModel.clearSubstituteForPeriod(period)
                                                            } else {
                                                                viewModel.selectSubstituteForPeriod(period, candidate.id)
                                                            }
                                                        }
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
                onValueChange = { reason = it },
                placeholder = { Text("Specify additional details for absence…", style = MaterialTheme.typography.bodySmall) },
                minLines = 3,
                maxLines = 5,
                shape = FaflowShapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = com.governence.faflow.ui.theme.FaflowBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(FaflowSpacing.xxl))

            // Submit Button
            val hasConflict = state.hasExistingLeaveOnDate && (isWholeDay || selectedPeriods.any { it in state.existingLeavePeriods })
            val isFlexibleCoverageOk = !state.isFlexibleMode || selectedPeriods.all { p ->
                state.selectedSubstitutePerPeriod.containsKey(p)
            }
            val isSubmitEnabled = reason.isNotBlank() && selectedPeriods.isNotEmpty() &&
                    !state.isBlockedDate && !hasConflict && isFlexibleCoverageOk

            if (state.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                val buttonText = when {
                    state.isTimetableLoaded && state.teacherSlots.isNotEmpty() && state.scheduledPeriodsForDate.isEmpty() && isWholeDay ->
                        "No Scheduled Classes to Cover"
                    selectedPeriods.isEmpty() ->
                        "Select Absence Period"
                    isWholeDay && selectedPeriods.size == 1 ->
                        "Submit Leave (1 Period Covered)"
                    isWholeDay ->
                        "Submit Whole Day Leave (${selectedPeriods.size} Periods)"
                    selectedPeriods.size == 1 ->
                        "Submit Leave (Period ${selectedPeriods.first()})"
                    else ->
                        "Submit Leave (${selectedPeriods.size} Periods)"
                }

                FaflowPillButton(
                    text = buttonText,
                    onClick = {
                        if (isSubmitEnabled) {
                            val periodsList = selectedPeriods.toList().sorted()
                            val periodSubstitutes = if (state.isFlexibleMode) {
                                state.selectedSubstitutePerPeriod
                                    .mapKeys { it.key.toString() }
                                    .ifEmpty { null }
                            } else null

                            if (periodsList.size > 1 || isWholeDay) {
                                viewModel.submitLeaveBatch(
                                    leaveDate, periodsList, reason, onLeaveSubmitted,
                                    periodSubstitutes = periodSubstitutes,
                                    wholeDay = isWholeDay
                                )
                            } else {
                                val proposedId = if (state.isFlexibleMode)
                                    state.selectedSubstitutePerPeriod[periodsList.first()]
                                else null
                                viewModel.submitLeave(
                                    leaveDate, periodsList.first(), reason, onLeaveSubmitted,
                                    proposedSubstituteId = proposedId
                                )
                            }
                        }
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
 * A single row showing a substitute candidate with workload badges.
 * Used in Flexible Mode during leave application.
 */
@Composable
private fun CandidatePickerRow(
    candidate: SlotCandidateOutDto,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val bg = if (isSelected) PrimaryBlue.copy(alpha = 0.10f) else Color.White
    val border = if (isSelected) PrimaryBlue else com.governence.faflow.ui.theme.FaflowBorder
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FaflowShapes.medium)
            .background(bg)
            .border(width = borderWidth, color = border, shape = FaflowShapes.medium)
            .clickable { onSelect() }
            .padding(horizontal = FaflowSpacing.md, vertical = FaflowSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(FaflowSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface
            )
            if (!candidate.departmentName.isNullOrBlank()) {
                Text(
                    text = candidate.departmentName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(FaflowSpacing.sm))
        // Workload badges
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "W:${candidate.weeklyAssignmentCount}",
                fontSize = 10.sp,
                color = if (candidate.weeklyAssignmentCount >= 4)
                    StatusError else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "D:${candidate.todayAssignmentCount}",
                fontSize = 10.sp,
                color = if (candidate.todayAssignmentCount >= 2)
                    StatusError else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = PrimaryBlue,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
