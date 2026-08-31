package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.data.prefs.SettingsRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.ReportSettings
import com.qareport.generator.domain.model.ThemeMode
import com.qareport.generator.ui.common.BaseViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : BaseViewModel() {

    val settings: StateFlow<ReportSettings> = settingsRepository.observeSettings()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
            settingsRepository.currentSettings()
        )

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)

    fun setIncludeLogs(enabled: Boolean) = settingsRepository.setIncludeLogs(enabled)

    fun setIncludeEvidence(enabled: Boolean) = settingsRepository.setIncludeEvidence(enabled)

    fun setIncludePassedCases(enabled: Boolean) = settingsRepository.setIncludePassedCases(enabled)

    fun setIncludeFailedCases(enabled: Boolean) = settingsRepository.setIncludeFailedCases(enabled)

    fun setIncludeAutoConclusion(enabled: Boolean) = settingsRepository.setIncludeAutoConclusion(enabled)

    fun setShowTechnicalLogs(enabled: Boolean) = settingsRepository.setShowTechnicalLogs(enabled)

    fun resetToDefaults() {
        settingsRepository.resetToDefaults()
        showMessage("Configuración restaurada.")
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(appContainer().settingsRepository) }
        }
    }
}
