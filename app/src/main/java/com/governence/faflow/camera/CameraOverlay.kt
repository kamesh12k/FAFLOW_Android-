package com.governence.faflow.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.governence.faflow.ui.theme.PrimaryBlue
import com.governence.faflow.ui.theme.SecondaryTeal
import com.governence.faflow.ui.theme.StatusError
import com.governence.faflow.ui.theme.StatusSuccess
import com.governence.faflow.ui.theme.StatusWarning
import com.governence.faflow.ui.viewmodels.FaceDetectionUiState

/**
 * Reusable facial positioning guide overlay with SCRFD detection state feedback.
 */
@Composable
fun CameraOverlay(
    cameraState: CameraState,
    faceDetectionState: FaceDetectionUiState = FaceDetectionUiState.NoFace,
    showDebugOverlay: Boolean = false,
    inferenceLatencyMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Oval Framing Guide
        val borderColor = when (faceDetectionState) {
            is FaceDetectionUiState.FacePositionValid -> StatusSuccess
            is FaceDetectionUiState.FaceDetected -> SecondaryTeal
            is FaceDetectionUiState.MultipleFaces -> StatusError
            is FaceDetectionUiState.FaceTooSmall, is FaceDetectionUiState.FaceTooLarge -> StatusWarning
            is FaceDetectionUiState.FacePartiallyOutOfFrame, is FaceDetectionUiState.FaceOutsideGuide -> StatusWarning
            else -> when (cameraState) {
                is CameraState.Ready, is CameraState.Processing -> Color.White.copy(alpha = 0.6f)
                is CameraState.Initializing -> Color.White.copy(alpha = 0.4f)
                else -> StatusError
            }
        }

        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 300.dp)
                .border(
                    width = 3.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(120.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(100.dp)
            )
        }

        // Developer Debug Overlay (Landmarks & Bounding Box)
        if (showDebugOverlay) {
            val face = when (faceDetectionState) {
                is FaceDetectionUiState.FacePositionValid -> faceDetectionState.primaryFace
                is FaceDetectionUiState.FaceDetected -> faceDetectionState.primaryFace
                else -> null
            }

            if (face != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scaleX = size.width / 640f
                    val scaleY = size.height / 480f

                    // Mirrored front-camera box
                    val left = (640f - face.boundingBox.right) * scaleX
                    val top = face.boundingBox.top * scaleY
                    val width = face.boundingBox.width * scaleX
                    val height = face.boundingBox.height * scaleY

                    drawRect(
                        color = Color.Green,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    face.landmarks?.toPointList()?.forEach { pt ->
                        val lx = (640f - pt.x) * scaleX
                        val ly = pt.y * scaleY
                        drawCircle(color = Color.Yellow, radius = 4.dp.toPx(), center = Offset(lx, ly))
                    }
                }
            }

            // Top Developer Diagnostics Banner
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "SCRFD 500M • Latency: ${inferenceLatencyMs}ms • Mode: CPU",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        // Bottom Status Badge Panel
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (faceDetectionState) {
                    is FaceDetectionUiState.FacePositionValid -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Face position valid",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is FaceDetectionUiState.FaceDetected -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = SecondaryTeal, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Face detected — position yourself inside the guide",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is FaceDetectionUiState.MultipleFaces -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Group, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Multiple faces detected — only one staff member should be visible",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is FaceDetectionUiState.FaceTooSmall -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.ZoomIn, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Move closer",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is FaceDetectionUiState.FaceTooLarge -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.ZoomOut, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Move farther away",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is FaceDetectionUiState.FacePartiallyOutOfFrame, is FaceDetectionUiState.FaceOutsideGuide -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Center your face",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    is FaceDetectionUiState.DetectionError -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = faceDetectionState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    FaceDetectionUiState.NoFace -> {
                        when (cameraState) {
                            is CameraState.Initializing -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Starting front camera...", style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            is CameraState.Ready, is CameraState.Processing -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.CameraAlt, contentDescription = null, tint = SecondaryTeal, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("No face detected", style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            is CameraState.Unavailable -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(cameraState.reason, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            is CameraState.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(cameraState.message, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}
