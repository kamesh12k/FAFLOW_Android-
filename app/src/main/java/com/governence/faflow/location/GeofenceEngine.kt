package com.governence.faflow.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Supported Geofence geometry shapes.
 */
enum class GeofenceType {
    CIRCLE,
    POLYGON
}

/**
 * Precise Geographic Coordinate (WGS84).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Campus Geofence Definition (Circle or Polygon).
 */
data class CampusGeofence(
    val id: String,
    val name: String,
    val type: GeofenceType = GeofenceType.CIRCLE,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double = 150.0,
    val polygonVertices: List<GeoPoint> = emptyList(),
    val toleranceMeters: Double = 15.0,
    val isActive: Boolean = true
)

/**
 * Result of Geofence evaluation against a staff member's live location.
 */
sealed class GeofenceValidationResult {
    data class Inside(
        val geofence: CampusGeofence,
        val distanceToCenterMeters: Double,
        val accuracyMeters: Float
    ) : GeofenceValidationResult()

    data class Outside(
        val nearestGeofence: CampusGeofence?,
        val distanceMeters: Double,
        val accuracyMeters: Float
    ) : GeofenceValidationResult()

    data class PoorAccuracy(
        val currentAccuracyMeters: Float,
        val requiredAccuracyMeters: Float = 30.0f
    ) : GeofenceValidationResult()

    data class MockLocationDetected(
        val provider: String?,
        val details: String = "Fake GPS / Mock Location detected"
    ) : GeofenceValidationResult()

    data object LocationDisabled : GeofenceValidationResult()
    data object PermissionDenied : GeofenceValidationResult()
    data object Loading : GeofenceValidationResult()
}

/**
 * Mathematical Engine for Geodesic distance (Haversine) and Point-In-Polygon (Ray Casting) computations.
 */
object GeofenceMathEngine {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates the great-circle distance between two geographic coordinates using the Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(rLat1) * cos(rLat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Evaluates whether a point is inside a circular geofence with optional margin tolerance.
     */
    fun isInsideCircle(
        point: GeoPoint,
        center: GeoPoint,
        radiusMeters: Double,
        toleranceMeters: Double = 0.0
    ): Pair<Boolean, Double> {
        val distance = calculateDistanceMeters(
            point.latitude,
            point.longitude,
            center.latitude,
            center.longitude
        )
        val isInside = distance <= (radiusMeters + toleranceMeters)
        return Pair(isInside, distance)
    }

    /**
     * Evaluates whether a point is inside a polygon using the Ray Casting Algorithm (Jordan Curve Theorem).
     */
    fun isInsidePolygon(
        point: GeoPoint,
        vertices: List<GeoPoint>
    ): Boolean {
        if (vertices.size < 3) return false

        var inside = false
        var j = vertices.size - 1

        for (i in vertices.indices) {
            val vi = vertices[i]
            val vj = vertices[j]

            val intersect = ((vi.latitude > point.latitude) != (vj.latitude > point.latitude)) &&
                    (point.longitude < (vj.longitude - vi.longitude) * (point.latitude - vi.latitude) / (vj.latitude - vi.latitude) + vi.longitude)

            if (intersect) {
                inside = !inside
            }
            j = i
        }

        return inside
    }
}
