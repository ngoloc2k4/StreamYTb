# ==============================================================================
# StreamYTb R8 / ProGuard Configuration Rules
# ==============================================================================

# Preserve line numbers and source files for readable stacktraces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ------------------------------------------------------------------------------
# Kotlin Coroutines & Flow
# ------------------------------------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# AndroidX Media3 / ExoPlayer
# ------------------------------------------------------------------------------
-dontwarn androidx.media3.**
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }
-keep class androidx.media3.ui.** { *; }

# Keep custom PlaybackService and SessionService lifecycle
-keep class com.example.player.PlaybackService { *; }
-keepclassmembers class * extends androidx.media3.session.MediaSessionService { *; }

# Keep native decoders and track selectors
-keepclasseswithmembers class * {
    native <methods>;
}

# ------------------------------------------------------------------------------
# Conscrypt & OkHttp
# ------------------------------------------------------------------------------
-dontwarn org.conscrypt.**
-keep class org.conscrypt.** { *; }
-keepclassmembers class org.conscrypt.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keepclassmembers class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    native <methods>;
}

# ------------------------------------------------------------------------------
# Room Database (SQLite)
# ------------------------------------------------------------------------------
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.migration.Migration
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public void <init>();
}
-keep class com.example.data.local.** { *; }

# ------------------------------------------------------------------------------
# Moshi & JSON Parsing
# ------------------------------------------------------------------------------
-dontwarn org.checkerframework.**
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keep class *JsonAdapter { *; }

# ------------------------------------------------------------------------------
# Coil Image Loader
# ------------------------------------------------------------------------------
-dontwarn coil.**
-keep class coil.** { *; }
-keepclassmembers class coil.** { *; }

# ------------------------------------------------------------------------------
# App Models & Entities
# ------------------------------------------------------------------------------
-keep class com.example.data.model.** { *; }
-keepclassmembers class com.example.data.model.** { *; }
-keep class com.example.StreamApp { *; }
