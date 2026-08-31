package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.data.prefs.SettingsRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Expone únicamente el modo de tema, para que la Activity pueda aplicarlo antes de
 * construir el resto del árbol de Compose sin depender de la pantalla de ajustes.
 */
class ThemeViewModel(settingsRepository: SettingsRepository) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.observeSettings()
        .map { it.themeMode }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            settingsRepository.currentSettings().themeMode
        )

    companion object {
        val Factory = viewModelFactory {
            initializer { ThemeViewModel(appContainer().settingsRepository) }
        }
    }
}
