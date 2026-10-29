# kotlinx.serialization: keep generated serializers for @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.aikeyboard.**$$serializer { *; }
-keepclassmembers class com.example.aikeyboard.** { *** Companion; }
-keepclasseswithmembers class com.example.aikeyboard.** { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp optional platforms
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Release builds never log; strip android.util.Log calls.
-assumenosideeffects class android.util.Log { public static *** d(...); public static *** v(...); public static *** i(...); }
