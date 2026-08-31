package com.gastos.quincenal.data.backup

/** Resultado de exportar o importar un respaldo. */
sealed interface BackupResult {
    data class Success(val message: String) : BackupResult
    data class Failure(val message: String) : BackupResult
}

/** Conteo de lo que contiene un respaldo. */
data class BackupStats(
    val categories: Int,
    val recurrences: Int,
    val expenses: Int,
    val incomes: Int,
    val expenseOverrides: Int,
    val incomeOverrides: Int,
    val months: Int
) {
    fun describe(): String =
        "$expenses gastos, $incomes ingresos, $categories categorias y $recurrences recurrencias"
}
