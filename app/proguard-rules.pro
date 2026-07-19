# ProGuard rules — add specific rules as dependencies require them
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Kotlin serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
