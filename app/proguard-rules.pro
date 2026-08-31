# Reglas de ofuscacion para el build de release.

# Room genera implementaciones en tiempo de compilacion; conservar sus metadatos.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Compose / Kotlin
-dontwarn kotlinx.coroutines.**
-keepclassmembers class ** {
    @androidx.compose.runtime.Composable <methods>;
}

# Modelos de dominio usados en exportacion JSON por nombre de campo.
-keep class com.qareport.generator.domain.model.** { *; }
