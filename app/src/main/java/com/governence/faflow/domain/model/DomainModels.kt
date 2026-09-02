package com.governence.faflow.domain.model

/**
 * Domain entity representing an enrolled individual (student, faculty, staff).
 */
data class Person(
    val id: String,
    val externalId: String,
    val name: String,
    val department: String,
    val className: String,
    val section: String,
    val email: String? = null,
    val phone: String? = null,
    val active: Boolean = true,
    val faceEnrolled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Biometric face profile storing embedding vectors and model metadata.
 */
data class FaceProfile(
    val id: String,
    val personId: String,
    val embedding: FloatArray,
    val modelName: String,
    val modelVersion: String,
    val qualityScore: Float,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FaceProfile

        if (id != other.id) return false
        if (personId != other.personId) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (modelName != other.modelName) return false
        if (modelVersion != other.modelVersion) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + personId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + modelName.hashCode()
        result = 31 * result + modelVersion.hashCode()
        return result
    }
}

/**
 * Attendance session initiated by a teacher/operator for a specific class or period.
 */
data class AttendanceSession(
    val id: String,
    val title: String,
    val classId: String,
    val subject: String,
    val operatorId: String,
    val operatorName: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val totalExpected: Int = 0,
    val presentCount: Int = 0
)

enum class SessionStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

/**
 * Individual attendance record generated during facial recognition.
 */
data class AttendanceRecord(
    val id: String,
    val sessionId: String,
    val personId: String,
    val personName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: AttendanceStatus = AttendanceStatus.PRESENT,
    val recognitionScore: Float = 0f,
    val livenessStatus: LivenessStatus = LivenessStatus.VERIFIED,
    val syncStatus: SyncStatus = SyncStatus.PENDING,
    val deviceId: String = "",
    val idempotencyKey: String = ""
)

enum class AttendanceStatus {
    PRESENT,
    LATE,
    ABSENT,
    REJECTED
}

enum class LivenessStatus {
    VERIFIED,
    SUSPICIOUS,
    SPOOF_DETECTED,
    BYPASSED
}

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}
