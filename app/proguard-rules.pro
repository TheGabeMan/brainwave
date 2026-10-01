# --- JavaMail (com.sun.mail:android-mail) -----------------------------------
# Providers and content handlers are looked up reflectively by name.
-dontwarn javax.activation.**
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.security.**
-dontwarn com.sun.activation.**
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.mail.** { *; }
-keep class com.sun.activation.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# --- kotlinx.serialization --------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- OkHttp -----------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- JNA + Vosk (on-device speech recognition) -------------------------------
# Vosk reaches its native library through JNA, and JNA's own native side finds
# its Java classes, fields and methods *by name* (Structure, CallbackReference,
# NativeMapped, WString, Pointer ...) while Vosk's native methods are matched to
# C functions by name too. R8 renaming or removing any of them does not give a
# build error: it gives a release APK that works in debug and crashes on the
# first transcription. So none of it may be renamed, shrunk or removed.
-dontwarn java.awt.**
-dontwarn javax.swing.**
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
