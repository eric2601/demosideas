package com.gastos.quincenal.data.demo

import com.gastos.quincenal.data.local.AppDatabase
import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.repository.SettingsRepository
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.utils.DateUtils
import java.time.YearMonth

/**
 * Datos de demostracion tomados de la hoja de referencia.
 *
 * Se cargan solo si el usuario acepta y se pueden borrar despues: todas las
 * filas quedan marcadas con `isDemo = true`.
 */
class DemoDataLoader(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository
) {

    /** Descripcion de un movimiento de ejemplo. */
    private data class DemoExpense(
        val name: String,
        val pesos: Double,
        val day: Int,
        val category: String,
        val type: ExpenseType = ExpenseType.VARIABLE
    )

    suspend fun load(yearMonth: YearMonth = YearMonth.now()) {
        val categories = db.categoryDao().getAll().associateBy { it.name }
        fun categoryId(name: String): Long? = categories[name]?.id

        val incomeDao = db.incomeDao()
        val expenseDao = db.expenseDao()

        // Ingresos: un sueldo por quincena.
        listOf(
            Triple("Sueldo", 13_600.0, 15),
            Triple("Sueldo", 9_722.0, 30)
        ).forEach { (name, pesos, day) ->
            incomeDao.upsert(
                IncomeEntity(
                    name = name,
                    amountCents = Money.ofPesos(pesos).cents,
                    date = DateUtils.clampDayOfMonth(yearMonth, day),
                    isDemo = true
                )
            )
        }

        (FIRST_QUINCENA + SECOND_QUINCENA).forEach { demo ->
            expenseDao.upsert(
                ExpenseEntity(
                    name = demo.name,
                    amountCents = Money.ofPesos(demo.pesos).cents,
                    categoryId = categoryId(demo.category),
                    date = DateUtils.clampDayOfMonth(yearMonth, demo.day),
                    type = demo.type,
                    isDemo = true
                )
            )
        }

        settingsRepository.setDemoDataLoaded(true)
    }

    /** Borra unicamente los movimientos marcados como demostracion. */
    suspend fun clear() {
        db.expenseDao().deleteDemo()
        db.incomeDao().deleteDemo()
        settingsRepository.setDemoDataLoaded(false)
    }

    private companion object {
        // Primera quincena de la hoja de referencia.
        // Los conceptos suman 12,014, no los 12,434 anotados en la hoja: se
        // respetan los importes tal como fueron proporcionados y la aplicacion
        // muestra la suma real (sobrante 1,586 sobre 13,600 de ingreso).
        val FIRST_QUINCENA = listOf(
            DemoExpense("Gasto", 2_600.0, 2, "Comida"),
            DemoExpense("Gasto", 600.0, 5, "Comida"),
            DemoExpense("Gasolina", 900.0, 10, "Gasolina"),
            DemoExpense("Movi", 210.0, 8, "Transporte"),
            DemoExpense("Elektra Ab", 840.0, 12, "Deudas", ExpenseType.DEBT),
            DemoExpense("Elektra", 500.0, 12, "Deudas", ExpenseType.DEBT),
            DemoExpense("Elektra", 800.0, 12, "Deudas", ExpenseType.DEBT),
            DemoExpense("Verificacion", 700.0, 14, "Transporte"),
            DemoExpense("Creditea", 1_114.0, 15, "Deudas", ExpenseType.DEBT),
            DemoExpense("Control", 1_500.0, 15, "Servicios", ExpenseType.FIXED),
            DemoExpense("Papa", 2_250.0, 15, "Familia")
        )

        // Segunda quincena de la hoja de referencia.
        // Los conceptos suman 8,554 (la hoja anota 8,954): sobrante real 1,168
        // sobre 9,722 de ingreso.
        val SECOND_QUINCENA = listOf(
            DemoExpense("Gasto", 2_800.0, 17, "Comida"),
            DemoExpense("Gasto", 600.0, 20, "Comida"),
            DemoExpense("Gasolina", 900.0, 24, "Gasolina"),
            DemoExpense("Elektra Ab", 840.0, 26, "Deudas", ExpenseType.DEBT),
            DemoExpense("Elektra", 500.0, 26, "Deudas", ExpenseType.DEBT),
            DemoExpense("Elektra", 800.0, 26, "Deudas", ExpenseType.DEBT),
            DemoExpense("Tenencia Carro", 1_000.0, 28, "Transporte"),
            DemoExpense("Creditea", 1_114.0, 30, "Deudas", ExpenseType.DEBT)
        )
    }
}
