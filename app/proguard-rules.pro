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
