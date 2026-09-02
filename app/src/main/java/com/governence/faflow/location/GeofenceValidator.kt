package com.governence.faflow.location

/**
 * Validates staff live GPS snapshots against institutional geofences.
 */
class GeofenceValidator(
    private val maxAccuracyThresholdMeters: Float = 30.0f
) {

    fun validateLocation(
        location: StaffLiveLocation?,
        geofences: List<CampusGeofence>
    ): GeofenceValidationResult {
        if (location == null) {
            return GeofenceValidationResult.Loading
        }

        // 1. Anti-Spoofing / Mock Location Verification
        if (location.isMock) {
            return GeofenceValidationResult.MockLocationDetected(
                provider = "Android Mock Location Provider",
                details = "Fake GPS / Spoofed location detected. Attendance cannot be registered."
            )
        }

        // 2. GPS Signal Quality & Accuracy Threshold Verification
        if (location.accuracy > maxAccuracyThresholdMeters) {
            return GeofenceValidationResult.PoorAccuracy(
                currentAccuracyMeters = location.accuracy,
                requiredAccuracyMeters = maxAccuracyThresholdMeters
            )
        }

        val activeGeofences = geofences.filter { it.isActive }
        if (activeGeofences.isEmpty()) {
            // Default fallback if no geofences configured
            return GeofenceValidationResult.Outside(
                nearestGeofence = null,
                distanceMeters = 0.0,
                accuracyMeters = location.accuracy
            )
        }

        val staffPoint = GeoPoint(location.latitude, location.longitude)
        var nearestGeofence: CampusGeofence? = null
        var minDistance = Double.MAX_VALUE

        for (geofence in activeGeofences) {
            when (geofence.type) {
                GeofenceType.CIRCLE -> {
                    val centerPoint = GeoPoint(geofence.centerLatitude, geofence.centerLongitude)
                    val (isInside, distance) = GeofenceMathEngine.isInsideCircle(
                        point = staffPoint,
                        center = centerPoint,
                        radiusMeters = geofence.radiusMeters,
                        toleranceMeters = geofence.toleranceMeters
                    )

                    if (distance < minDistance) {
                        minDistance = distance
                        nearestGeofence = geofence
                    }

                    if (isInside) {
                        return GeofenceValidationResult.Inside(
                            geofence = geofence,
                            distanceToCenterMeters = distance,
                            accuracyMeters = location.accuracy
                        )
                    }
                }

                GeofenceType.POLYGON -> {
                    val isInside = GeofenceMathEngine.isInsidePolygon(
                        point = staffPoint,
                        vertices = geofence.polygonVertices
                    )

                    val centerPoint = GeoPoint(geofence.centerLatitude, geofence.centerLongitude)
                    val distance = GeofenceMathEngine.calculateDistanceMeters(
                        staffPoint.latitude,
                        staffPoint.longitude,
                        centerPoint.latitude,
                        centerPoint.longitude
                    )

                    if (distance < minDistance) {
                        minDistance = distance
                        nearestGeofence = geofence
                    }

                    if (isInside) {
                        return GeofenceValidationResult.Inside(
                            geofence = geofence,
                            distanceToCenterMeters = distance,
                            accuracyMeters = location.accuracy
                        )
                    }
                }
            }
        }

        // If outside all active geofences, report distance to nearest campus perimeter
        return GeofenceValidationResult.Outside(
            nearestGeofence = nearestGeofence,
            distanceMeters = minDistance,
            accuracyMeters = location.accuracy
        )
    }
}
