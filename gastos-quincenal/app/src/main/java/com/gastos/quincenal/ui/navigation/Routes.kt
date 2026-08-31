package com.gastos.quincenal.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/** Rutas de navegacion de la aplicacion. */
object Routes {
    const val DASHBOARD = "dashboard"
    const val CALENDAR = "calendar"
    const val EXPENSES = "expenses"
    const val INCOMES = "incomes"
    const val STATISTICS = "statistics"
    const val SETTINGS = "settings"
    const val HISTORY = "history"

    const val QUINCENA_DETAIL = "quincena/{year}/{month}/{quincena}"
    const val EXPENSE_EDITOR = "expense-editor?expenseId={expenseId}&date={date}"
    const val INCOME_EDITOR = "income-editor?incomeId={incomeId}&date={date}"

    fun quincenaDetail(year: Int, month: Int, quincena: String): String =
        "quincena/$year/$month/$quincena"

    fun expenseEditor(expenseId: Long = 0L, date: String? = null): String =
        "expense-editor?expenseId=$expenseId&date=${date.orEmpty()}"

    fun incomeEditor(incomeId: Long = 0L, date: String? = null): String =
        "income-editor?incomeId=$incomeId&date=${date.orEmpty()}"

    object Args {
        const val YEAR = "year"
        const val MONTH = "month"
        const val QUINCENA = "quincena"
        const val EXPENSE_ID = "expenseId"
        const val INCOME_ID = "incomeId"
        const val DATE = "date"
    }
}

/** Destino de la barra inferior. */
data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomDestinations = listOf(
    BottomDestination(Routes.DASHBOARD, "Inicio", Icons.Filled.Home),
    BottomDestination(Routes.CALENDAR, "Calendario", Icons.Filled.CalendarMonth),
    BottomDestination(Routes.EXPENSES, "Gastos", Icons.Filled.Payments),
    BottomDestination(Routes.INCOMES, "Ingresos", Icons.Filled.TrendingUp),
    BottomDestination(Routes.SETTINGS, "Ajustes", Icons.Filled.Settings)
)
