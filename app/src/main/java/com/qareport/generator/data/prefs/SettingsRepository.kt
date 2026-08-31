package com.qareport.generator.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.qareport.generator.domain.model.ReportSettings
import com.qareport.generator.domain.model.ThemeMode
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Preferencias de la aplicación.
 *
 * Se usa [SharedPreferences] en lugar de DataStore para no añadir una dependencia
 * extra: el volumen de preferencias es mínimo y la lectura reactiva se resuelve con un
 * [callbackFlow] sobre el listener de cambios.
 */
class SettingsRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun observeSettings(): Flow<ReportSettings> = callbackFlow {
        trySend(currentSettings())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(currentSettings())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun currentSettings(): ReportSettings = try {
        ReportSettings(
            themeMode = ThemeMode.from(preferences.getString(KEY_THEME, ThemeMode.SYSTEM.key)),
            includeLogs = preferences.getBoolean(KEY_INCLUDE_LOGS, true),
            includeEvidence = preferences.getBoolean(KEY_INCLUDE_EVIDENCE, true),
            includePassedCases = preferences.getBoolean(KEY_INCLUDE_PASSED, true),
            includeFailedCases = preferences.getBoolean(KEY_INCLUDE_FAILED, true),
            includeAutoConclusion = preferences.getBoolean(KEY_INCLUDE_CONCLUSION, true),
            showTechnicalLogs = preferences.getBoolean(KEY_SHOW_LOGS, false)
        )
    } catch (error: ClassCastException) {
        // Preferencias corruptas: se restauran los valores por defecto en vez de fallar.
        AppLogger.warn("Preferencias inválidas, se restauran los valores por defecto", error)
        preferences.edit().clear().apply()
        ReportSettings()
    }

    fun setThemeMode(mode: ThemeMode) = edit { putString(KEY_THEME, mode.key) }

    fun setIncludeLogs(enabled: Boolean) = edit { putBoolean(KEY_INCLUDE_LOGS, enabled) }

    fun setIncludeEvidence(enabled: Boolean) = edit { putBoolean(KEY_INCLUDE_EVIDENCE, enabled) }

    fun setIncludePassedCases(enabled: Boolean) = edit { putBoolean(KEY_INCLUDE_PASSED, enabled) }

    fun setIncludeFailedCases(enabled: Boolean) = edit { putBoolean(KEY_INCLUDE_FAILED, enabled) }

    fun setIncludeAutoConclusion(enabled: Boolean) = edit { putBoolean(KEY_INCLUDE_CONCLUSION, enabled) }

    fun setShowTechnicalLogs(enabled: Boolean) = edit { putBoolean(KEY_SHOW_LOGS, enabled) }

    fun resetToDefaults() = edit { clear() }

    private inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        preferences.edit().apply(block).apply()
    }

    companion object {
        private const val PREFS_NAME = "qa_report_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_INCLUDE_LOGS = "include_logs"
        private const val KEY_INCLUDE_EVIDENCE = "include_evidence"
        private const val KEY_INCLUDE_PASSED = "include_passed"
        private const val KEY_INCLUDE_FAILED = "include_failed"
        private const val KEY_INCLUDE_CONCLUSION = "include_conclusion"
        private const val KEY_SHOW_LOGS = "show_technical_logs"
    }
}
