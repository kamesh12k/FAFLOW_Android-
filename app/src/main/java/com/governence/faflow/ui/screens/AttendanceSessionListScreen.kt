package com.governence.faflow.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.governence.faflow.domain.model.AttendanceSession
import com.governence.faflow.domain.model.SessionStatus
import com.governence.faflow.ui.components.AppTopBar
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.theme.StatusWarning

@Composable
fun AttendanceSessionListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToStartAttendance: () -> Unit,
    onNavigateToSessionDetails: (String) -> Unit
) {
    val sampleSessions = listOf(
        AttendanceSession(id = "SES-101", title = "Computer Vision 101", classId = "CS-4A", subject = "Deep Learning", operatorId = "OP-1", operatorName = "Prof. Ramesh", status = SessionStatus.COMPLETED, totalExpected = 45, presentCount = 42),
        AttendanceSession(id = "SES-102", title = "Database Systems", classId = "CS-4B", subject = "Distributed SQL", operatorId = "OP-1", operatorName = "Prof. Ramesh", status = SessionStatus.COMPLETED, totalExpected = 50, presentCount = 48),
        AttendanceSession(id = "SES-103", title = "Operating Systems", classId = "IT-3A", subject = "Kernel Architecture", operatorId = "OP-1", operatorName = "Prof. Ramesh", status = SessionStatus.ACTIVE, totalExpected = 40, presentCount = 38)
    )

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Attendance Sessions",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToStartAttendance,
                containerColor = PrimaryBlue,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Start New Session")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleSessions) { session ->
                SessionListItem(session = session, onClick = { onNavigateToSessionDetails(session.id) })
            }
        }
    }
}

@Composable
fun SessionListItem(session: AttendanceSession, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${session.classId} • ${session.subject}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Present: ${session.presentCount} / ${session.totalExpected}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.status == SessionStatus.ACTIVE) StatusWarning else StatusSuccess,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
