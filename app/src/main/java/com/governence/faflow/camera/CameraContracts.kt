package com.governence.faflow.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LifecycleOwner

/**
 * Camera lens facing configuration.
 */
enum class CameraLens {
    FRONT,
    BACK
}

/**
 * Camera runtime state representation.
 */
sealed interface CameraState {
    data object Idle : CameraState
    data object Initializing : CameraState
    data object Streaming : CameraState
    data class Error(val errorType: CameraError, val message: String) : CameraState
}

enum class CameraError {
    PERMISSION_DENIED,
    NO_CAMERA_AVAILABLE,
    INITIALIZATION_FAILED,
    FRAME_ANALYSIS_FAILED,
    LENS_SWITCH_FAILED
}

/**
 * Encapsulated frame metadata passed to background processors.
 */
data class FrameData(
    val bitmap: Bitmap,
    val rotationDegrees: Int,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Frame analyzer callback interface for CameraX analysis use-case.
 */
fun interface FrameProcessor {
    fun process(frameData: FrameData)
}

/**
 * Contract for managing CameraX lifecycle, surface binding, and lens switching.
 */
interface CameraManager {
    val currentState: CameraState
    val currentLens: CameraLens

    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: androidx.camera.core.Preview.SurfaceProvider,
        frameProcessor: FrameProcessor?
    )
    fun switchCamera()
    fun stopCamera()
}
