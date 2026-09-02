package com.governence.faflow.location

import com.governence.faflow.domain.model.CampusGeofence

/**
 * Encapsulated high-accuracy GPS coordinates with accuracy and mock status.
 */
data class StaffLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val isMock: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface GeofenceResult {
    data class Inside(val geofence: CampusGeofence, val distanceMeters: Float) : GeofenceResult
    data class Outside(val nearestGeofence: CampusGeofence?, val distanceMeters: Float) : GeofenceResult
    data class MockDetected(val reason: String) : GeofenceResult
    data class Error(val message: String) : GeofenceResult
}

/**
 * Contract for location acquisition and mock detection.
 */
interface LocationProvider {
    suspend fun getCurrentLocation(): Result<StaffLocation>
    fun isLocationPermissionGranted(): Boolean
}

/**
 * Contract for validating staff position against institutional campus boundaries.
 */
interface GeofenceValidator {
    fun validateLocation(location: StaffLocation, activeGeofences: List<CampusGeofence>): GeofenceResult
    fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float
}
