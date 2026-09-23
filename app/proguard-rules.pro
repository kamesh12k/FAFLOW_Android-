# =====================================================================
# FAFLOW Staff Mobile: Optimized Production ProGuard & R8 Rules
# Optimized according to Android Skills guidelines (r8-analyzer)
# =====================================================================

# Global attribute preservation for Kotlin, reflection, and serialization
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# ONNX Runtime Mobile (InsightFace SCRFD & ArcFace ONNX inference + JNI types)
-keep class ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Biometrics ML & Face Recognition Pipeline
-keep class com.governence.faflow.attendance.biometrics.** { *; }
-keepclassmembers class com.governence.faflow.attendance.biometrics.** { *; }

# Kotlin Metadata & Reflection (Required by Moshi KotlinJsonAdapterFactory)
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-keep interface kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.**

# Moshi JSON Serialization & Reflection
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Keep all DTO models completely (needed by Moshi KotlinJsonAdapterFactory for fields, constructors, and generic signatures)
-keep class com.governence.faflow.core.network.** { *; }
-keepclassmembers class com.governence.faflow.core.network.** { *; }
-keep class com.governence.faflow.attendance.student.data.** { *; }
-keepclassmembers class com.governence.faflow.attendance.student.data.** { *; }
-keep class com.governence.faflow.domain.model.** { *; }
-keepclassmembers class com.governence.faflow.domain.model.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.Json <methods>;
}

# Android Jetpack Security (Warnings suppression; consumer rules bundled in AAR)
-dontwarn androidx.security.crypto.**

# Network Stack Warnings Suppressions (Retrofit & OkHttp provide their own consumer keep rules)
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# AndroidX Room (Reflection-instantiated database implementations)
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keepclassmembers class * extends androidx.room.RoomDatabase {
    *;
}
-dontwarn androidx.room.paging.**

# AndroidX WorkManager & Custom CoroutineWorkers
-keep class androidx.work.impl.** { *; }
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.Worker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.CoroutineWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-dontwarn androidx.work.impl.**

# AndroidX Startup
-keep class * extends androidx.startup.Initializer {
    <init>();
}
