# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep Room database and DAOs
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Keep data models and entities from being stripped in release mode
-keep class com.example.data.model.** { *; }
-keep class com.example.data.entity.** { *; }
-keep class com.example.service.DownloadProgressEvent** { *; }

# Keep OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Coil image loader
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Coroutines
-keepnames class kotlinx.coroutines.** { *; }
