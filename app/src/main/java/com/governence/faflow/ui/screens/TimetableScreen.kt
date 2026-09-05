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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.governence.faflow.domain.model.TimetableSlot
import com.governence.faflow.ui.components.ErrorRetryView
import com.governence.faflow.ui.components.FaflowSectionHeader
import com.governence.faflow.ui.components.FaflowStatusBadge
import com.governence.faflow.ui.components.FaflowSurface
import com.governence.faflow.ui.theme.FaflowShapes
import com.governence.faflow.ui.theme.FaflowSpacing
import com.governence.faflow.ui.theme.FaflowStatusColors
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.viewmodels.TimetableViewModel

/**
 * Modern Academic Timetable Screen for FAFLOW.
 * Clean, minimal schedule view with Day Order pill selectors,
 * explicit Room / Subject / Class hierarchy, and clear free-period states.
 */
@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

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
                        text = "Weekly Timetable",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Academic schedule by Day Order",
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
        ) {
            // Day Order Selector Pill Bar (Day Order 1 through 6)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FaflowSpacing.lg, vertical = FaflowSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(FaflowSpacing.sm)
            ) {
                items((1..6).toList()) { day ->
                    val isSelected = day == state.selectedDayOrder
                    val pillBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val pillFg = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                    val borderCol = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

                    Box(
                        modifier = Modifier
                            .clip(FaflowShapes.pill)
                            .background(pillBg)
                            .border(width = 1.dp, color = borderCol, shape = FaflowShapes.pill)
                            .clickable { viewModel.selectDayOrder(day) }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Day $day",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = pillFg
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(FaflowSpacing.xs))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                }
            } else if (state.errorMessage != null && state.allSlots.isEmpty()) {
                ErrorRetryView(
                    message = state.errorMessage!!,
                    onRetry = { viewModel.retry() }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = FaflowSpacing.lg, vertical = FaflowSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(FaflowSpacing.md)
                ) {
                    item {
                        FaflowSectionHeader(
                            title = "Day Order ${state.selectedDayOrder} Schedule",
                            subtitle = "${state.daySlots.size} teaching periods assigned"
                        )
                    }

                    items((1..5).toList()) { period ->
                        val slot = state.daySlots.find { it.periodNumber == period }
                        TimetablePeriodItem(period = period, slot = slot)
                    }

                    item {
                        Spacer(modifier = Modifier.height(FaflowSpacing.xxl))
                    }
                }
            }
        }
    }
}

@Composable
fun TimetablePeriodItem(period: Int, slot: TimetableSlot?) {
    val periodTimes = when (period) {
        1 -> "08:45 - 09:40"
        2 -> "09:40 - 10:35"
        3 -> "10:50 - 11:45"
        4 -> "11:45 - 12:40"
        5 -> "01:30 - 02:25"
        else -> "Slot $period"
    }

    if (slot != null) {
        FaflowSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(FaflowSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Period Indicator
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp)
                ) {
                    Text(
                        text = "P0$period",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryBlue
                    )
                    Text(
                        text = periodTimes.substringBefore(" -"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.width(FaflowSpacing.md))

                // Divider line
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(PrimaryBlue.copy(alpha = 0.2f))
                )

                Spacer(modifier = Modifier.width(FaflowSpacing.md))

                // Subject & Class Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.subjectName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${slot.className} (${slot.section})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.MeetingRoom,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Room ${slot.roomNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                FaflowStatusBadge(
                    text = slot.subjectCode,
                    statusColor = PrimaryBlue,
                    showDot = false
                )
            }
        }
    } else {
        // Free / Research Period
        FaflowSurface(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            contentPadding = PaddingValues(FaflowSpacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp)
                ) {
                    Text(
                        text = "P0$period",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = periodTimes.substringBefore(" -"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.width(FaflowSpacing.md))

                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                )

                Spacer(modifier = Modifier.width(FaflowSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Free Period",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Research, syllabus preparation & grading",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                FaflowStatusBadge(
                    text = "Open",
                    statusColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    showDot = false
                )
            }
        }
    }
}
