package com.governence.faflow.core.telemetry

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Non-sensitive technical performance and operational metrics collector.
 * PRIVACY GUARANTEE: Never logs or collects biometric images, vectors, or sensitive user data.
 */
object AttendanceTelemetry {

    private val metricsMap = ConcurrentHashMap<String, AtomicLong>()
    private val eventsMap = ConcurrentHashMap<String, String>()
    private var isTelemetryEnabled = true

    fun setEnabled(enabled: Boolean) {
        isTelemetryEnabled = enabled
    }

    fun recordMetric(metricName: String, durationMs: Long) {
        if (!isTelemetryEnabled) return
        metricsMap.computeIfAbsent(metricName) { AtomicLong(0) }.set(durationMs)
    }

    fun recordEvent(eventName: String, eventValue: String) {
        if (!isTelemetryEnabled) return
        eventsMap[eventName] = eventValue
    }

    fun getMetric(metricName: String): Long {
        return metricsMap[metricName]?.get() ?: 0L
    }

    fun getEvent(eventName: String): String? {
        return eventsMap[eventName]
    }

    fun clear() {
        metricsMap.clear()
        eventsMap.clear()
    }

    // Telemetry metric keys
    const val METRIC_GPS_ACQUISITION_MS = "gps_acquisition_latency_ms"
    const val METRIC_SCRFD_DETECTION_MS = "scrfd_face_detection_latency_ms"
    const val METRIC_UMEYAMA_ALIGNMENT_MS = "umeyama_alignment_latency_ms"
    const val METRIC_ARCFACE_EMBEDDING_MS = "arcface_embedding_latency_ms"
    const val METRIC_LIVENESS_VERIFICATION_MS = "liveness_verification_latency_ms"
    const val METRIC_NETWORK_SUBMISSION_MS = "network_submission_latency_ms"
}
