package com.gastos.quincenal.di

import android.content.Context
import com.gastos.quincenal.data.backup.BackupRepository
import com.gastos.quincenal.data.demo.DemoDataLoader
import com.gastos.quincenal.data.local.AppDatabase
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.data.repository.SettingsRepository
import com.gastos.quincenal.domain.usecase.DuplicateMonthUseCase
import com.gastos.quincenal.domain.usecase.GetHistoryUseCase
import com.gastos.quincenal.domain.usecase.GetMonthOverviewUseCase
import com.gastos.quincenal.domain.usecase.SaveExpenseUseCase
import com.gastos.quincenal.domain.usecase.SaveIncomeUseCase

/**
 * Inyeccion de dependencias manual.
 *
 * El proyecto no usa Hilt a proposito: el grafo es pequeno, se construye una
 * sola vez y asi el build no depende de otro procesador de anotaciones.
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.getInstance(context)

    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(database) }
    val backupRepository: BackupRepository by lazy {
        BackupRepository(context.applicationContext, database)
    }
    val demoDataLoader: DemoDataLoader by lazy { DemoDataLoader(database, settingsRepository) }

    val getMonthOverview: GetMonthOverviewUseCase by lazy {
        GetMonthOverviewUseCase(budgetRepository, settingsRepository)
    }
    val saveExpense: SaveExpenseUseCase by lazy { SaveExpenseUseCase(budgetRepository) }
    val saveIncome: SaveIncomeUseCase by lazy { SaveIncomeUseCase(budgetRepository) }
    val duplicateMonth: DuplicateMonthUseCase by lazy { DuplicateMonthUseCase(budgetRepository) }
    val getHistory: GetHistoryUseCase by lazy { GetHistoryUseCase(budgetRepository) }
}
