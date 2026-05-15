# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ──────────────────────────────────────────────────────────────────────────────
# Networking — Retrofit / OkHttp / Moshi
# ──────────────────────────────────────────────────────────────────────────────
# These rules are required for the release build to keep working if/when
# `isMinifyEnabled = true` is flipped on. They are no-ops while minify is off.

# ── Retrofit ──────────────────────────────────────────────────────────────────
# Retrofit uses reflection on the service interfaces and on the generic return
# types of suspend functions. Strip nothing from them.
-keep,allowobfuscation,allowshrinking interface com.eflglobal.visitorsapp.data.remote.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Retrofit ships its own consumer rules but pin the core types just in case.
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
# OkHttp 4.x is Kotlin-first; consumer rules cover most of it. Silence warnings
# from optional dependencies we don't ship (Conscrypt, BouncyCastle JSSE, etc.)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Moshi ────────────────────────────────────────────────────────────────────-
# We use the codegen factory (KSP-generated *_JsonAdapter classes) AND the
# reflective KotlinJsonAdapterFactory as a fallback. Both paths need protection.
-keep class com.squareup.moshi.** { *; }
-keep interface com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-dontwarn com.squareup.moshi.**

# KSP-generated Moshi adapters live next to the DTO class with a `JsonAdapter`
# suffix. Keep all of them.
-keep class **JsonAdapter { *; }
-keepclassmembers class **JsonAdapter {
    <init>(...);
    <fields>;
}

# Keep DTOs the API serialises/deserialises. Reflection-based Moshi needs the
# constructors and the field metadata.
-keep,allowobfuscation,allowshrinking class com.eflglobal.visitorsapp.data.remote.dto.** { *; }
-keepclassmembers class com.eflglobal.visitorsapp.data.remote.dto.** {
    <init>(...);
    <fields>;
}

# Kotlin metadata is required for the reflective KotlinJsonAdapterFactory path.
-keepattributes *Annotation*
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ──────────────────────────────────────────────────────────────────────────────
# Coroutines
# ──────────────────────────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ──────────────────────────────────────────────────────────────────────────────
# Room
# ──────────────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-dontwarn androidx.room.paging.**

# ──────────────────────────────────────────────────────────────────────────────
# App-level entities used reflectively by Room / Moshi
# ──────────────────────────────────────────────────────────────────────────────
-keep class com.eflglobal.visitorsapp.data.local.entity.** { *; }
-keep enum com.eflglobal.visitorsapp.** { *; }
