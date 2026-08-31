package com.gastos.quincenal.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Categoria de gasto o ingreso.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name", "forIncome"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val forIncome: Boolean = false,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Regla de recurrencia. Se guarda UNA vez y el motor genera las fechas del mes
 * consultado; nunca se replican filas por cada mes futuro.
 */
@Entity(tableName = "recurrences")
data class RecurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val frequency: Frequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val dayOfMonth: Int? = null,
    val dayOfWeek: DayOfWeek? = null,
    val active: Boolean = true
)

/**
 * Definicion de un gasto.
 *
 * Si [recurrenceId] es nulo el gasto ocurre una sola vez, en [date].
 * Si no lo es, [date] actua como fecha ancla de la regla y las ocurrencias del
 * mes se calculan al vuelo.
 *
 * Los importes se guardan en centavos para evitar errores de punto flotante.
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RecurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurrenceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId"), Index("recurrenceId"), Index("date")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val amountCents: Long,
    val categoryId: Long?,
    val date: LocalDate,
    val type: ExpenseType = ExpenseType.VARIABLE,
    val isRecurring: Boolean = false,
    val recurrenceId: Long? = null,
    /** Estado por defecto; cada ocurrencia puede sobrescribirlo. */
    val isPaid: Boolean = false,
    val notes: String = "",
    val active: Boolean = true,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** Definicion de un ingreso. Misma logica de recurrencia que el gasto. */
@Entity(
    tableName = "incomes",
    foreignKeys = [
        ForeignKey(
            entity = RecurrenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurrenceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("recurrenceId"), Index("date")]
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val amountCents: Long,
    val date: LocalDate,
    val isRecurring: Boolean = false,
    val recurrenceId: Long? = null,
    val isReceived: Boolean = false,
    val notes: String = "",
    val active: Boolean = true,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Excepcion aplicada a UNA ocurrencia concreta de un gasto recurrente.
 *
 * Permite marcar como pagado el recibo de marzo, cambiarle el importe o saltarlo
 * sin romper la regla ni duplicar la serie completa.
 */
@Entity(
    tableName = "expense_overrides",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["expenseId", "occurrenceDate"], unique = true)]
)
data class ExpenseOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val expenseId: Long,
    val occurrenceDate: LocalDate,
    val amountCents: Long? = null,
    val isPaid: Boolean? = null,
    val movedToDate: LocalDate? = null,
    val notes: String? = null,
    val skipped: Boolean = false
)

/** Excepcion aplicada a una ocurrencia concreta de un ingreso recurrente. */
@Entity(
    tableName = "income_overrides",
    foreignKeys = [
        ForeignKey(
            entity = IncomeEntity::class,
            parentColumns = ["id"],
            childColumns = ["incomeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["incomeId", "occurrenceDate"], unique = true)]
)
data class IncomeOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val incomeId: Long,
    val occurrenceDate: LocalDate,
    val amountCents: Long? = null,
    val isReceived: Boolean? = null,
    val movedToDate: LocalDate? = null,
    val notes: String? = null,
    val skipped: Boolean = false
)

/**
 * Datos propios de un mes concreto (nota del usuario y cierre).
 * La clave es el mes en formato "2026-01".
 */
@Entity(tableName = "months")
data class MonthEntity(
    @PrimaryKey val yearMonth: String,
    val note: String = "",
    val closed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/** Preferencias de la aplicacion. Siempre existe exactamente una fila con id = 1. */
@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = SETTINGS_ID,
    val currencySymbol: String = "$",
    val currencyCode: String = "MXN",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Umbral en centavos por debajo del cual se avisa de sobrante bajo. */
    val lowRemainingThresholdCents: Long = 50_000L,
    val demoDataLoaded: Boolean = false,
    val onboardingCompleted: Boolean = false
) {
    companion object {
        const val SETTINGS_ID = 1
    }
}
