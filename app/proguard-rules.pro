# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp

# LiteRT (low-level runtime — MediaPipe wraps this)
-keep class org.tensorflow.lite.** { *; }
-keep class com.google.ai.edge.litert.** { *; }

# MediaPipe LLM Inference
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Tink
-keep class com.google.crypto.tink.** { *; }

# Duecare model classes — Room entities, journal data, intel domain knowledge
-keep class com.duecare.journey.journal.** { *; }
-keep class com.duecare.journey.intel.** { *; }
-keep class com.duecare.journey.inference.** { *; }
-keep class com.duecare.journey.harness.** { *; }

# Kotlin metadata for reflection-based features
-keepattributes *Annotation*, RuntimeVisibleAnnotations, Signature, EnclosingMethod
