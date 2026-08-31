package com.gastos.quincenal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.ui.calendar.CalendarScreen
import com.gastos.quincenal.ui.dashboard.DashboardScreen
import com.gastos.quincenal.ui.expenses.ExpenseEditorScreen
import com.gastos.quincenal.ui.expenses.ExpenseListScreen
import com.gastos.quincenal.ui.history.HistoryScreen
import com.gastos.quincenal.ui.income.IncomeEditorScreen
import com.gastos.quincenal.ui.income.IncomeListScreen
import com.gastos.quincenal.ui.quincena.QuincenaDetailScreen
import com.gastos.quincenal.ui.settings.SettingsScreen
import com.gastos.quincenal.ui.statistics.StatisticsScreen
import com.gastos.quincenal.utils.DateUtils
import java.time.YearMonth

/** Grafo de navegacion completo de la aplicacion. */
@Composable
fun GastosNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenQuincena = { yearMonth, quincena ->
                    navController.navigate(
                        Routes.quincenaDetail(yearMonth.year, yearMonth.monthValue, quincena.name)
                    )
                },
                onOpenStatistics = { navController.navigate(Routes.STATISTICS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onEditExpense = { id, date ->
                    navController.navigate(Routes.expenseEditor(id, DateUtils.toIso(date)))
                }
            )
        }

        composable(Routes.CALENDAR) {
            CalendarScreen(
                onAddExpense = { date ->
                    navController.navigate(Routes.expenseEditor(date = DateUtils.toIso(date)))
                },
                onEditExpense = { id, date ->
                    navController.navigate(Routes.expenseEditor(id, DateUtils.toIso(date)))
                }
            )
        }

        composable(Routes.EXPENSES) {
            ExpenseListScreen(
                onAddExpense = { navController.navigate(Routes.expenseEditor()) },
                onEditExpense = { id, date ->
                    navController.navigate(Routes.expenseEditor(id, DateUtils.toIso(date)))
                },
                onOpenQuincena = { yearMonth, quincena ->
                    navController.navigate(
                        Routes.quincenaDetail(yearMonth.year, yearMonth.monthValue, quincena.name)
                    )
                }
            )
        }

        composable(Routes.INCOMES) {
            IncomeListScreen(
                onAddIncome = { navController.navigate(Routes.incomeEditor()) },
                onEditIncome = { id, date ->
                    navController.navigate(Routes.incomeEditor(id, DateUtils.toIso(date)))
                }
            )
        }

        composable(Routes.STATISTICS) {
            StatisticsScreen(
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen()
        }

        composable(
            route = Routes.QUINCENA_DETAIL,
            arguments = listOf(
                navArgument(Routes.Args.YEAR) { type = NavType.IntType },
                navArgument(Routes.Args.MONTH) { type = NavType.IntType },
                navArgument(Routes.Args.QUINCENA) { type = NavType.StringType }
            )
        ) { entry ->
            val year = entry.arguments?.getInt(Routes.Args.YEAR) ?: YearMonth.now().year
            val month = entry.arguments?.getInt(Routes.Args.MONTH) ?: YearMonth.now().monthValue
            val quincena = runCatching {
                Quincena.valueOf(entry.arguments?.getString(Routes.Args.QUINCENA).orEmpty())
            }.getOrDefault(Quincena.PRIMERA)

            QuincenaDetailScreen(
                yearMonth = YearMonth.of(year, month),
                quincena = quincena,
                onBack = { navController.popBackStack() },
                onAddExpense = { date ->
                    navController.navigate(Routes.expenseEditor(date = DateUtils.toIso(date)))
                },
                onAddIncome = { date ->
                    navController.navigate(Routes.incomeEditor(date = DateUtils.toIso(date)))
                },
                onEditExpense = { id, date ->
                    navController.navigate(Routes.expenseEditor(id, DateUtils.toIso(date)))
                }
            )
        }

        composable(
            route = Routes.EXPENSE_EDITOR,
            arguments = listOf(
                navArgument(Routes.Args.EXPENSE_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(Routes.Args.DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            ExpenseEditorScreen(
                expenseId = entry.arguments?.getLong(Routes.Args.EXPENSE_ID) ?: 0L,
                initialDate = DateUtils.parseIsoOrNull(entry.arguments?.getString(Routes.Args.DATE)),
                onDone = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.INCOME_EDITOR,
            arguments = listOf(
                navArgument(Routes.Args.INCOME_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(Routes.Args.DATE) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            IncomeEditorScreen(
                incomeId = entry.arguments?.getLong(Routes.Args.INCOME_ID) ?: 0L,
                initialDate = DateUtils.parseIsoOrNull(entry.arguments?.getString(Routes.Args.DATE)),
                onDone = { navController.popBackStack() }
            )
        }
    }
}
