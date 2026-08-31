package com.gastos.quincenal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.gastos.quincenal.data.local.dao.CategoryDao
import com.gastos.quincenal.data.local.dao.ExpenseDao
import com.gastos.quincenal.data.local.dao.IncomeDao
import com.gastos.quincenal.data.local.dao.RecurrenceDao
import com.gastos.quincenal.data.local.dao.SettingsDao
import com.gastos.quincenal.data.local.entity.CategoryEntity
import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.ExpenseOverrideEntity
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.local.entity.IncomeOverrideEntity
import com.gastos.quincenal.data.local.entity.MonthEntity
import com.gastos.quincenal.data.local.entity.RecurrenceEntity
import com.gastos.quincenal.data.local.entity.UserSettingsEntity

/**
 * Base de datos local. Toda la informacion financiera vive aqui, en el
 * dispositivo: la aplicacion no declara el permiso de Internet.
 */
@Database(
    entities = [
        CategoryEntity::class,
        RecurrenceEntity::class,
        ExpenseEntity::class,
        ExpenseOverrideEntity::class,
        IncomeEntity::class,
        IncomeOverrideEntity::class,
        MonthEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun recurrenceDao(): RecurrenceDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val DATABASE_NAME = "gastos_quincenal.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .build()

        /** Instancia en memoria usada por las pruebas instrumentadas. */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .build()
    }
}
