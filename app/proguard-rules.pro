# Room Database: Keep the generated implementation classes and the data entities
# Room relies on reflection to instantiate your database and DAOs.
-keep class * extends androidx.room.RoomDatabase
-keep class com.revive.app.data.** { *; }
-dontwarn androidx.room.**

# Notification Listener Service:
# The system identifies and binds to this service using the class name declared in the Manifest.
# It must NOT be renamed or stripped.
-keep class com.revive.app.service.MessageNotificationListener { *; }

# Keep Entry Points: The Application and Activity classes used by the system.
-keep class com.revive.app.MainActivity { *; }
-keep class com.revive.app.ReviveApp { *; }

# Jetpack Compose & Metadata: Preserve attributes required for Compose reflection and Kotlin metadata.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# Coil: Keep image loading classes if they are being stripped.
-keep class coil.** { *; }
-dontwarn coil.**