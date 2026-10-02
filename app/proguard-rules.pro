# ==============================================================================
# PROGUARD / R8 RULES FOR SAM AI APPLICATION
# ==============================================================================

# Keep line numbers and source file names for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve all annotations and generic signatures
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# 1. ROOM DATABASE (@Entity, @Dao, RoomDatabase)
# ------------------------------------------------------------------------------
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keepclassmembers class * {
    @androidx.room.Insert *;
    @androidx.room.Update *;
    @androidx.room.Delete *;
    @androidx.room.Query *;
    @androidx.room.RawQuery *;
}
-keep class com.example.data.local.** { *; }
-keep class * implements androidx.room.RoomDatabase
-keep class * extends androidx.room.migration.Migration

# ------------------------------------------------------------------------------
# 2. DATA MODELS & SERIALIZATION (Kotlinx Serialization, Moshi, JSON)
# ------------------------------------------------------------------------------
-dontwarn kotlinx.serialization.**
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    *** INSTANCE;
}
-keep class kotlinx.serialization.** { *; }

# Application Data & Remote Entities
-keep class com.example.data.remote.** { *; }
-keep class com.example.data.cache.** { *; }
-keep class com.example.data.repository.** { *; }

# ------------------------------------------------------------------------------
# 3. NETWORKING (Retrofit, OkHttp)
# ------------------------------------------------------------------------------
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okio.**
-keep class okio.** { *; }

# ------------------------------------------------------------------------------
# 4. KOTLIN COROUTINES & STATEFLOW
# ------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.flow.** { *; }

# ------------------------------------------------------------------------------
# 5. FIREBASE & GOOGLE PLAY SERVICES
# ------------------------------------------------------------------------------
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }
-keepclassmembers class com.google.firebase.** { *; }

-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }
-keepclassmembers class com.google.android.gms.** { *; }

-dontwarn androidx.credentials.**
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# ------------------------------------------------------------------------------
# 6. JETPACK COMPOSE
# ------------------------------------------------------------------------------
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keep class * extends androidx.activity.ComponentActivity
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ------------------------------------------------------------------------------
# 7. MEDIA & THIRD-PARTY LIBRARIES (Coil, YouTube, Generative AI)
# ------------------------------------------------------------------------------
-dontwarn coil.**
-keep class coil.** { *; }

-dontwarn com.pierfrancescosoffritti.androidyoutubeplayer.**
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }

-dontwarn com.google.ai.client.generativeai.**
-keep class com.google.ai.client.generativeai.** { *; }

# ------------------------------------------------------------------------------
# 8. BUILDCONFIG & SYSTEM REFLECTION
# ------------------------------------------------------------------------------
-keep class com.example.BuildConfig { *; }
