package com.gastos.quincenal.domain.model

import java.time.DayOfWeek

/** Frecuencia con la que se repite un gasto o un ingreso. */
enum class Frequency {
    /** Solo este mes: ocurre una unica vez en la fecha indicada. */
    ONCE,

    /** Cada semana (o cada N semanas si el intervalo es mayor que 1). */
    WEEKLY,

    /** Cada dos semanas. Equivale a WEEKLY con intervalo 2. */
    BIWEEKLY,

    /** Cada mes (o cada N meses). */
    MONTHLY,

    /** Cada ano, en el mismo mes y dia. */
    YEARLY;

    val displayName: String
        get() = when (this) {
            ONCE -> "Solo una vez"
            WEEKLY -> "Cada semana"
            BIWEEKLY -> "Cada 2 semanas"
            MONTHLY -> "Cada mes"
            YEARLY -> "Cada ano"
        }

    /** Indica si la frecuencia necesita que el usuario elija un dia de la semana. */
    val needsDayOfWeek: Boolean get() = this == WEEKLY || this == BIWEEKLY
}

/** Hasta cuando se repite una regla de recurrencia. */
enum class RecurrenceEnd {
    /** Solo este mes (sin repeticion). */
    ONLY_THIS_MONTH,

    /** Repetir indefinidamente. */
    FOREVER,

    /** Repetir hasta una fecha limite inclusive. */
    UNTIL_DATE;

    val displayName: String
        get() = when (this) {
            ONLY_THIS_MONTH -> "Solo este mes"
            FOREVER -> "Repetir indefinidamente"
            UNTIL_DATE -> "Repetir hasta una fecha"
        }
}

/** Clasificacion de un gasto segun su naturaleza. */
enum class ExpenseType {
    FIXED,
    VARIABLE,
    DEBT,
    SUBSCRIPTION;

    val displayName: String
        get() = when (this) {
            FIXED -> "Fijo"
            VARIABLE -> "Variable"
            DEBT -> "Deuda"
            SUBSCRIPTION -> "Suscripcion"
        }
}

/** Estado de pago de una ocurrencia concreta. */
enum class PaymentStatus {
    PENDING,
    PAID;

    val displayName: String get() = if (this == PAID) "Pagado" else "Pendiente"
}

/** Preferencia de tema de la aplicacion. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    val displayName: String
        get() = when (this) {
            SYSTEM -> "Seguir al sistema"
            LIGHT -> "Claro"
            DARK -> "Oscuro"
        }
}

/** Nombres en espanol de los dias de la semana, empezando en domingo. */
object DayOfWeekNames {
    val ORDER: List<DayOfWeek> = listOf(
        DayOfWeek.SUNDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY
    )

    fun short(day: DayOfWeek): String = when (day) {
        DayOfWeek.SUNDAY -> "DOM"
        DayOfWeek.MONDAY -> "LUN"
        DayOfWeek.TUESDAY -> "MAR"
        DayOfWeek.WEDNESDAY -> "MIE"
        DayOfWeek.THURSDAY -> "JUE"
        DayOfWeek.FRIDAY -> "VIE"
        DayOfWeek.SATURDAY -> "SAB"
    }

    fun full(day: DayOfWeek): String = when (day) {
        DayOfWeek.SUNDAY -> "Domingo"
        DayOfWeek.MONDAY -> "Lunes"
        DayOfWeek.TUESDAY -> "Martes"
        DayOfWeek.WEDNESDAY -> "Miercoles"
        DayOfWeek.THURSDAY -> "Jueves"
        DayOfWeek.FRIDAY -> "Viernes"
        DayOfWeek.SATURDAY -> "Sabado"
    }
}
