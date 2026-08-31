package com.gastos.quincenal.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.gastos.quincenal.GastosApplication
import com.gastos.quincenal.ui.calendar.CalendarViewModel
import com.gastos.quincenal.ui.dashboard.DashboardViewModel
import com.gastos.quincenal.ui.expenses.ExpenseEditorViewModel
import com.gastos.quincenal.ui.expenses.ExpenseListViewModel
import com.gastos.quincenal.ui.history.HistoryViewModel
import com.gastos.quincenal.ui.income.IncomeEditorViewModel
import com.gastos.quincenal.ui.income.IncomeListViewModel
import com.gastos.quincenal.ui.quincena.QuincenaViewModel
import com.gastos.quincenal.ui.settings.SettingsViewModel
import com.gastos.quincenal.ui.statistics.StatisticsViewModel

/** Acceso al contenedor desde las factorias de ViewModel. */
private val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as GastosApplication).container

/**
 * Factorias de todos los ViewModel de la aplicacion.
 *
 * Se agrupan aqui para que las pantallas solo declaren `viewModel(factory = ...)`
 * y ningun ViewModel tenga que buscar sus dependencias por su cuenta.
 */
object ViewModelFactories {

    val dashboard: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            DashboardViewModel(
                getMonthOverview = container.getMonthOverview,
                budgetRepository = container.budgetRepository,
                duplicateMonth = container.duplicateMonth
            )
        }
    }

    val calendar: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            CalendarViewModel(
                getMonthOverview = container.getMonthOverview,
                budgetRepository = container.budgetRepository
            )
        }
    }

    val expenseList: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ExpenseListViewModel(
                getMonthOverview = container.getMonthOverview,
                budgetRepository = container.budgetRepository
            )
        }
    }

    val expenseEditor: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ExpenseEditorViewModel(
                budgetRepository = container.budgetRepository,
                saveExpense = container.saveExpense,
                settingsRepository = container.settingsRepository
            )
        }
    }

    val incomeList: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            IncomeListViewModel(
                getMonthOverview = container.getMonthOverview,
                budgetRepository = container.budgetRepository
            )
        }
    }

    val incomeEditor: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            IncomeEditorViewModel(
                budgetRepository = container.budgetRepository,
                saveIncome = container.saveIncome,
                settingsRepository = container.settingsRepository
            )
        }
    }

    val quincena: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            QuincenaViewModel(
                getMonthOverview = container.getMonthOverview,
                budgetRepository = container.budgetRepository
            )
        }
    }

    val statistics: ViewModelProvider.Factory = viewModelFactory {
        initializer { StatisticsViewModel(getMonthOverview = container.getMonthOverview) }
    }

    val history: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            HistoryViewModel(
                getHistory = container.getHistory,
                settingsRepository = container.settingsRepository
            )
        }
    }

    val settings: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(
                settingsRepository = container.settingsRepository,
                budgetRepository = container.budgetRepository,
                backupRepository = container.backupRepository,
                demoDataLoader = container.demoDataLoader,
                duplicateMonthUseCase = container.duplicateMonth
            )
        }
    }
}
