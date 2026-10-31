# Moshi
-keep class com.freevibe.data.remote.** { *; }
-keepclassmembers class com.freevibe.data.remote.** { *; }
-keep class com.freevibe.service.FavoriteExportItem { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room builds the generated database through its no-arg constructor. AGP 9's strict full
# mode no longer keeps that constructor implicitly.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *

# NewPipe Extractor + Rhino JS
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn jdk.dynalink.**
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }

# yt-dlp / youtubedl-android + Apache Commons Compress
-keep class com.yausername.** { *; }
-keepclassmembers class com.yausername.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# R8 can nondeterministically merge this abstract Jackson serializer, changing
# its synthetic discriminator by one byte between otherwise identical builds.
# Keep it shrinkable/obfuscatable but do not optimize or merge the class.
-keep,allowshrinking,allowobfuscation class com.fasterxml.jackson.databind.ser.std.ToStringSerializerBase { *; }
