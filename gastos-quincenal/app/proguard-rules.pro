# Room genera implementaciones por reflexion en tiempo de compilacion; no requiere reglas.
# Se conservan los enums porque se serializan por nombre en el respaldo JSON.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Modelos usados en el respaldo JSON.
-keep class com.gastos.quincenal.data.local.entity.** { *; }
-keep class com.gastos.quincenal.domain.model.** { *; }
