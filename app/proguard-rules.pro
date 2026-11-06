# R8 / ProGuard rules for the Aegis release build.
# Anything read or written by reflection must be kept, otherwise the signed APK crashes or sends empty JSON
# even though the debug build works.

# Gson and Retrofit read these classes by field name and generic type.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# API request/response models, domain models and the cached-listing snapshot (Gson).
-keep class com.example.aegis.data.remote.dto.** { *; }
-keep class com.example.aegis.domain.model.** { *; }
-keep class com.example.aegis.data.local.db.ListingSnapshot { *; }

# Retrofit service interface.
-keep interface com.example.aegis.data.remote.AegisApi { *; }

# Kotlin coroutines continuation type used by Retrofit suspend functions.
-keep class kotlin.coroutines.Continuation

# Enums are looked up by name (valueOf).
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }

# Keep readable stack traces in crash reports.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
