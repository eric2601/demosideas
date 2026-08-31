package com.gastos.quincenal.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.data.backup.BackupRepository
import com.gastos.quincenal.data.backup.BackupResult
import com.gastos.quincenal.data.demo.DemoDataLoader
import com.gastos.quincenal.data.repository.AppSettings
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.data.repository.SettingsRepository
import com.gastos.quincenal.domain.model.Category
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.ThemeMode
import com.gastos.quincenal.domain.usecase.DuplicateMonthUseCase
import com.gastos.quincenal.domain.usecase.DuplicateOptions
import com.gastos.quincenal.ui.common.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Pantalla de configuracion: tema, moneda, categorias, respaldo y datos de ejemplo. */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val budgetRepository: BudgetRepository,
    private val backupRepository: BackupRepository,
    private val demoDataLoader: DemoDataLoader,
    private val duplicateMonthUseCase: DuplicateMonthUseCase
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), AppSettings())

    val categories: StateFlow<List<Category>> = budgetRepository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCurrency(symbol: String, code: String) {
        viewModelScope.launch {
            settingsRepository.setCurrency(symbol, code)
            _message.value = UiMessage.of("Moneda actualizada.")
        }
    }

    fun setLowRemainingThreshold(text: String) {
        val amount = Money.parse(text)
        if (amount == null) {
            _message.value = UiMessage.of("Escribe una cantidad valida para el aviso.")
            return
        }
        viewModelScope.launch {
            settingsRepository.setLowRemainingThreshold(amount)
            _message.value = UiMessage.of("Aviso de sobrante bajo actualizado.")
        }
    }

    fun saveCategory(category: Category) {
        if (category.name.isBlank()) {
            _message.value = UiMessage.of("La categoria necesita un nombre.")
            return
        }
        viewModelScope.launch {
            budgetRepository.saveCategory(category)
            _message.value = UiMessage.of("Categoria guardada.")
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            budgetRepository.deleteCategory(category)
            _message.value = UiMessage.of("Categoria eliminada. Sus gastos quedan sin categoria.")
        }
    }

    fun loadDemoData() {
        viewModelScope.launch {
            _isBusy.value = true
            demoDataLoader.load(YearMonth.now())
            _isBusy.value = false
            _message.value = UiMessage.of("Datos de ejemplo cargados en el mes actual.")
        }
    }

    fun clearDemoData() {
        viewModelScope.launch {
            _isBusy.value = true
            demoDataLoader.clear()
            _isBusy.value = false
            _message.value = UiMessage.of("Datos de ejemplo eliminados.")
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = backupRepository.export(uri)
            _isBusy.value = false
            _message.value = UiMessage.of(result.text())
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = backupRepository.import(uri)
            _isBusy.value = false
            _message.value = UiMessage.of(result.text())
        }
    }

    fun eraseAllData() {
        viewModelScope.launch {
            _isBusy.value = true
            backupRepository.wipeAll()
            settingsRepository.ensureInitialized()
            settingsRepository.setDemoDataLoaded(false)
            _isBusy.value = false
            _message.value = UiMessage.of("Se borro toda la informacion.")
        }
    }

    /** Duplica un mes con las opciones elegidas por el usuario. */
    fun duplicateMonth(source: YearMonth, target: YearMonth, options: DuplicateOptions) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = duplicateMonthUseCase(source, target, options)
            _isBusy.value = false
            _message.value = UiMessage.of(result.describe(target))
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun BackupResult.text(): String = when (this) {
        is BackupResult.Success -> message
        is BackupResult.Failure -> message
    }
}
