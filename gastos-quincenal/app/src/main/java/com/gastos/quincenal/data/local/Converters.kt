package com.gastos.quincenal.data.local

import androidx.room.TypeConverter
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.ThemeMode
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Conversores de Room.
 *
 * Las fechas se guardan como dia epoch (entero) porque ordena y compara
 * directamente en SQL y no depende de la zona horaria del dispositivo.
 * Los enums se guardan por nombre para que el respaldo JSON sea legible y
 * para que agregar valores nuevos no rompa los datos existentes.
 */
class Converters {

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun dayOfWeekToInt(day: DayOfWeek?): Int? = day?.value

    @TypeConverter
    fun intToDayOfWeek(value: Int?): DayOfWeek? =
        value?.takeIf { it in 1..7 }?.let(DayOfWeek::of)

    @TypeConverter
    fun frequencyToString(frequency: Frequency): String = frequency.name

    @TypeConverter
    fun stringToFrequency(value: String): Frequency =
        runCatching { Frequency.valueOf(value) }.getOrDefault(Frequency.ONCE)

    @TypeConverter
    fun expenseTypeToString(type: ExpenseType): String = type.name

    @TypeConverter
    fun stringToExpenseType(value: String): ExpenseType =
        runCatching { ExpenseType.valueOf(value) }.getOrDefault(ExpenseType.VARIABLE)

    @TypeConverter
    fun themeModeToString(mode: ThemeMode): String = mode.name

    @TypeConverter
    fun stringToThemeMode(value: String): ThemeMode =
        runCatching { ThemeMode.valueOf(value) }.getOrDefault(ThemeMode.SYSTEM)
}
