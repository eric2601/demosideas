package com.gastos.quincenal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gastos.quincenal.data.backup.BackupPayload
import com.gastos.quincenal.data.backup.BackupSerializer
import com.gastos.quincenal.data.local.DefaultCategories
import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.ExpenseOverrideEntity
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.local.entity.IncomeOverrideEntity
import com.gastos.quincenal.data.local.entity.MonthEntity
import com.gastos.quincenal.data.local.entity.RecurrenceEntity
import com.gastos.quincenal.data.local.entity.UserSettingsEntity
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * El respaldo se prueba como prueba instrumentada porque org.json forma parte
 * del sistema Android y no del JDK.
 */
@RunWith(AndroidJUnit4::class)
class BackupSerializerTest {

    private val payload = BackupPayload(
        version = BackupSerializer.CURRENT_VERSION,
        exportedAt = 1_760_000_000_000L,
        settings = UserSettingsEntity(
            currencySymbol = "$",
            currencyCode = "MXN",
            themeMode = ThemeMode.DARK,
            lowRemainingThresholdCents = 75_000L,
            demoDataLoaded = true,
            onboardingCompleted = true
        ),
        categories = DefaultCategories.ALL.take(3).mapIndexed { index, category ->
            category.copy(id = index + 1L)
        },
        recurrences = listOf(
            RecurrenceEntity(
                id = 1L,
                frequency = Frequency.WEEKLY,
                interval = 2,
                startDate = LocalDate.of(2026, 1, 2),
                endDate = LocalDate.of(2026, 12, 31),
                dayOfWeek = DayOfWeek.FRIDAY
            ),
            RecurrenceEntity(
                id = 2L,
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.of(2026, 1, 1),
                dayOfMonth = 1
            )
        ),
        expenses = listOf(
            ExpenseEntity(
                id = 1L,
                name = "Comida",
                amountCents = 60_000L,
                categoryId = 3L,
                date = LocalDate.of(2026, 1, 2),
                type = ExpenseType.VARIABLE,
                isRecurring = true,
                recurrenceId = 1L,
                notes = "Cada viernes"
            ),
            ExpenseEntity(
                id = 2L,
                name = "Tenencia",
                amountCents = 100_000L,
                categoryId = null,
                date = LocalDate.of(2026, 1, 28)
            )
        ),
        incomes = listOf(
            IncomeEntity(
                id = 1L,
                name = "Sueldo",
                amountCents = 1_360_000L,
                date = LocalDate.of(2026, 1, 15),
                isRecurring = true,
                recurrenceId = 2L
            )
        ),
        expenseOverrides = listOf(
            ExpenseOverrideEntity(
                id = 1L,
                expenseId = 1L,
                occurrenceDate = LocalDate.of(2026, 2, 6),
                amountCents = 80_000L,
                isPaid = true
            )
        ),
        incomeOverrides = listOf(
            IncomeOverrideEntity(
                id = 1L,
                incomeId = 1L,
                occurrenceDate = LocalDate.of(2026, 3, 15),
                skipped = true
            )
        ),
        months = listOf(MonthEntity(yearMonth = "2026-01", note = "Mes de referencia"))
    )

    @Test
    fun el_respaldo_conserva_toda_la_informacion() {
        val restored = BackupSerializer.decode(BackupSerializer.encode(payload))

        assertEquals(payload.version, restored.version)
        assertEquals(payload.settings, restored.settings)
        assertEquals(payload.categories, restored.categories)
        assertEquals(payload.recurrences, restored.recurrences)
        assertEquals(payload.expenses, restored.expenses)
        assertEquals(payload.incomes, restored.incomes)
        assertEquals(payload.expenseOverrides, restored.expenseOverrides)
        assertEquals(payload.incomeOverrides, restored.incomeOverrides)
        assertEquals(payload.months, restored.months)
    }

    @Test
    fun los_campos_opcionales_nulos_se_conservan_como_nulos() {
        val restored = BackupSerializer.decode(BackupSerializer.encode(payload))
        val once = restored.expenses.first { it.id == 2L }

        assertNull(once.categoryId)
        assertNull(once.recurrenceId)
        assertNull(restored.recurrences.first { it.id == 2L }.endDate)
        assertNull(restored.recurrences.first { it.id == 2L }.dayOfWeek)
        assertNull(restored.incomeOverrides.first().amountCents)
    }

    @Test
    fun las_fechas_se_escriben_en_formato_iso() {
        val json = BackupSerializer.encode(payload)

        assertTrue(json.contains("\"2026-01-02\""))
        assertTrue(json.contains("\"2026-12-31\""))
        assertTrue(json.contains("\"WEEKLY\""))
    }
}
