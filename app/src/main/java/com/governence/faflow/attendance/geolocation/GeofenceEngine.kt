package com.governence.faflow.attendance.geolocation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Supported Geofence geometry types.
 */
enum class GeofenceType {
    CIRCLE,
    POLYGON
}

/**
 * High-precision WGS84 coordinate.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Institutional Campus Geofence Definition.
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
 * Core mathematical engine for spherical geodesy (Haversine) and Point-In-Polygon (Jordan Curve).
 */
object GeofenceMathEngine {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates great-circle distance between two coordinates in meters.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        if (lat1 == lat2 && lon1 == lon2) return 0.0

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
     * Checks if a point is inside a circle, on its boundary (within tolerance), or outside.
     * Returns: Pair(isInside, isBoundary) and distance in meters.
     */
    fun evaluateCircle(
        point: GeoPoint,
        center: GeoPoint,
        radiusMeters: Double,
        toleranceMeters: Double = 15.0
    ): Triple<Boolean, Boolean, Double> {
        val distance = calculateDistanceMeters(
            point.latitude,
            point.longitude,
            center.latitude,
            center.longitude
        )

        val isStrictlyInside = distance < (radiusMeters - toleranceMeters)
        val isBoundary = distance in (radiusMeters - toleranceMeters)..(radiusMeters + toleranceMeters)
        val isInsideWithTolerance = distance <= (radiusMeters + toleranceMeters)

        return Triple(isInsideWithTolerance, isBoundary, distance)
    }

    /**
     * Point-In-Polygon evaluation using the Ray Casting algorithm.
     * Returns true if point is strictly inside polygon.
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

    /**
     * Calculates the minimum geodesic distance in meters from a point to the perimeter of a polygon.
     */
    fun distanceToPolygonMeters(
        point: GeoPoint,
        vertices: List<GeoPoint>
    ): Double {
        val n = vertices.size
        if (n < 2) {
            return if (n == 1) {
                calculateDistanceMeters(point.latitude, point.longitude, vertices[0].latitude, vertices[0].longitude)
            } else {
                0.0
            }
        }

        val latRad = Math.toRadians(point.latitude)
        val cosLat = kotlin.math.cos(latRad)
        val latToM = (Math.PI / 180.0) * EARTH_RADIUS_METERS
        val lonToM = latToM * cosLat

        val px = point.longitude * lonToM
        val py = point.latitude * latToM

        var minDistSq = Double.MAX_VALUE

        for (i in 0 until n) {
            val v1 = vertices[i]
            val v2 = vertices[(i + 1) % n]

            val x1 = v1.longitude * lonToM
            val y1 = v1.latitude * latToM
            val x2 = v2.longitude * lonToM
            val y2 = v2.latitude * latToM

            val dx = x2 - x1
            val dy = y2 - y1
            val segLenSq = dx * dx + dy * dy

            val distSq = if (segLenSq < 1e-12) {
                (px - x1) * (px - x1) + (py - y1) * (py - y1)
            } else {
                val t = (((px - x1) * dx + (py - y1) * dy) / segLenSq).coerceIn(0.0, 1.0)
                val projX = x1 + t * dx
                val projY = y1 + t * dy
                (px - projX) * (px - projX) + (py - projY) * (py - projY)
            }

            if (distSq < minDistSq) {
                minDistSq = distSq
            }
        }

        return kotlin.math.sqrt(minDistSq)
    }
}
