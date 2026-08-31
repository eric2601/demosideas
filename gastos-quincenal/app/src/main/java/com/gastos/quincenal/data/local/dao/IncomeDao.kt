package com.gastos.quincenal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.local.entity.IncomeOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {

    @Query(
        """
        SELECT * FROM incomes
        WHERE active = 1
          AND (
                (recurrenceId IS NULL AND date BETWEEN :fromEpochDay AND :toEpochDay)
             OR (recurrenceId IS NOT NULL AND date <= :toEpochDay)
          )
        ORDER BY date ASC, id ASC
        """
    )
    fun observeCandidates(fromEpochDay: Long, toEpochDay: Long): Flow<List<IncomeEntity>>

    @Query(
        """
        SELECT * FROM incomes
        WHERE active = 1
          AND (
                (recurrenceId IS NULL AND date BETWEEN :fromEpochDay AND :toEpochDay)
             OR (recurrenceId IS NOT NULL AND date <= :toEpochDay)
          )
        ORDER BY date ASC, id ASC
        """
    )
    suspend fun getCandidates(fromEpochDay: Long, toEpochDay: Long): List<IncomeEntity>

    @Query("SELECT * FROM incomes ORDER BY date ASC, id ASC")
    fun observeAll(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes")
    suspend fun getAll(): List<IncomeEntity>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun getById(id: Long): IncomeEntity?

    /** Ver la nota de ExpenseDao.upsert: evita el borrado en cascada de REPLACE. */
    @Upsert
    suspend fun upsert(income: IncomeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(incomes: List<IncomeEntity>): List<Long>

    @Update
    suspend fun update(income: IncomeEntity)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM incomes WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM incomes")
    suspend fun deleteAll()

    // -------------------- Excepciones por ocurrencia --------------------

    @Query("SELECT * FROM income_overrides WHERE occurrenceDate BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeOverrides(fromEpochDay: Long, toEpochDay: Long): Flow<List<IncomeOverrideEntity>>

    @Query("SELECT * FROM income_overrides")
    suspend fun getAllOverrides(): List<IncomeOverrideEntity>

    @Query("SELECT * FROM income_overrides WHERE incomeId = :incomeId AND occurrenceDate = :epochDay LIMIT 1")
    suspend fun getOverride(incomeId: Long, epochDay: Long): IncomeOverrideEntity?

    @Upsert
    suspend fun upsertOverride(override: IncomeOverrideEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverrides(overrides: List<IncomeOverrideEntity>): List<Long>

    @Query("DELETE FROM income_overrides WHERE incomeId = :incomeId AND occurrenceDate = :epochDay")
    suspend fun deleteOverride(incomeId: Long, epochDay: Long)

    @Query("DELETE FROM income_overrides")
    suspend fun deleteAllOverrides()
}
