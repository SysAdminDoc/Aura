# Optional classes referenced by the accessibility test stack are not exercised
# by the legacy Android NewPipe release gate.
-dontwarn androidx.appcompat.graphics.drawable.DrawableWrapper
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder
