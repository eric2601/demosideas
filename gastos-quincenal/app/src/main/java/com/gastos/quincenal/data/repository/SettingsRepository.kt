package com.gastos.quincenal.data.repository

import com.gastos.quincenal.data.local.AppDatabase
import com.gastos.quincenal.data.local.DefaultCategories
import com.gastos.quincenal.data.local.entity.MonthEntity
import com.gastos.quincenal.data.local.entity.UserSettingsEntity
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.ThemeMode
import com.gastos.quincenal.utils.CurrencyFormatter
import com.gastos.quincenal.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

/** Preferencias de la aplicacion, ya convertidas a modelo de dominio. */
data class AppSettings(
    val currencySymbol: String = CurrencyFormatter.DEFAULT_SYMBOL,
    val currencyCode: String = CurrencyFormatter.DEFAULT_CODE,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lowRemainingThreshold: Money = Money(50_000L),
    val demoDataLoaded: Boolean = false,
    val onboardingCompleted: Boolean = false
) {
    fun formatter(): CurrencyFormatter = CurrencyFormatter(currencySymbol, currencyCode)
}

/**
 * Acceso a la fila unica de preferencias y a las notas por mes.
 * Tambien se encarga de sembrar el estado inicial la primera vez.
 */
class SettingsRepository(private val db: AppDatabase) {

    private val dao = db.settingsDao()
    private val categoryDao = db.categoryDao()

    fun observeSettings(): Flow<AppSettings> =
        dao.observeSettings().map { it?.toDomain() ?: AppSettings() }

    suspend fun getSettings(): AppSettings = dao.getSettings()?.toDomain() ?: AppSettings()

    /**
     * Garantiza que exista la fila de preferencias y el catalogo inicial de
     * categorias. Es idempotente: se puede llamar en cada arranque.
     */
    suspend fun ensureInitialized() {
        if (dao.getSettings() == null) {
            dao.upsert(UserSettingsEntity())
        }
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(DefaultCategories.ALL)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }

    suspend fun setCurrency(symbol: String, code: String) = update {
        it.copy(
            currencySymbol = symbol.ifBlank { CurrencyFormatter.DEFAULT_SYMBOL },
            currencyCode = code.ifBlank { CurrencyFormatter.DEFAULT_CODE }
        )
    }

    suspend fun setLowRemainingThreshold(threshold: Money) = update {
        it.copy(lowRemainingThresholdCents = threshold.cents.coerceAtLeast(0L))
    }

    suspend fun setDemoDataLoaded(loaded: Boolean) = update { it.copy(demoDataLoaded = loaded) }

    suspend fun setOnboardingCompleted(completed: Boolean) = update {
        it.copy(onboardingCompleted = completed)
    }

    suspend fun replaceSettings(settings: AppSettings) {
        dao.upsert(
            UserSettingsEntity(
                currencySymbol = settings.currencySymbol,
                currencyCode = settings.currencyCode,
                themeMode = settings.themeMode,
                lowRemainingThresholdCents = settings.lowRemainingThreshold.cents,
                demoDataLoaded = settings.demoDataLoaded,
                onboardingCompleted = settings.onboardingCompleted
            )
        )
    }

    fun observeMonthNote(yearMonth: YearMonth): Flow<String> =
        dao.observeMonth(DateUtils.yearMonthToIso(yearMonth)).map { it?.note.orEmpty() }

    suspend fun setMonthNote(yearMonth: YearMonth, note: String) {
        dao.upsertMonth(MonthEntity(yearMonth = DateUtils.yearMonthToIso(yearMonth), note = note))
    }

    suspend fun getMonths(): List<MonthEntity> = dao.getMonths()

    suspend fun replaceMonths(months: List<MonthEntity>) {
        dao.deleteAllMonths()
        dao.upsertMonths(months)
    }

    private suspend fun update(transform: (UserSettingsEntity) -> UserSettingsEntity) {
        val current = dao.getSettings() ?: UserSettingsEntity()
        dao.upsert(transform(current))
    }

    private fun UserSettingsEntity.toDomain() = AppSettings(
        currencySymbol = currencySymbol,
        currencyCode = currencyCode,
        themeMode = themeMode,
        lowRemainingThreshold = Money(lowRemainingThresholdCents),
        demoDataLoaded = demoDataLoaded,
        onboardingCompleted = onboardingCompleted
    )
}
