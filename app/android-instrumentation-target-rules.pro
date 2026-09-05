# Android test dependencies shared with the target APK are omitted from the
# test APK. Keep their runtime classes in minified release targets used by the
# legacy-device instrumentation lane.
-keep class androidx.tracing.** { *; }
-keep class kotlin.** { *; }
-keep class com.freevibe.data.repository.YouTubeRepositoryKt { *; }
