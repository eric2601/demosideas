package com.gastos.quincenal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.ExpenseOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    /**
     * Definiciones que pueden aportar ocurrencias al rango [fromEpochDay, toEpochDay]:
     * los gastos unicos cuya fecha cae dentro y todos los recurrentes que ya
     * empezaron. El filtrado fino lo hace el motor de recurrencia.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE active = 1
          AND (
                (recurrenceId IS NULL AND date BETWEEN :fromEpochDay AND :toEpochDay)
             OR (recurrenceId IS NOT NULL AND date <= :toEpochDay)
          )
        ORDER BY date ASC, id ASC
        """
    )
    fun observeCandidates(fromEpochDay: Long, toEpochDay: Long): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE active = 1
          AND (
                (recurrenceId IS NULL AND date BETWEEN :fromEpochDay AND :toEpochDay)
             OR (recurrenceId IS NOT NULL AND date <= :toEpochDay)
          )
        ORDER BY date ASC, id ASC
        """
    )
    suspend fun getCandidates(fromEpochDay: Long, toEpochDay: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY date ASC, id ASC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: Long): Flow<ExpenseEntity?>

    /**
     * Inserta o actualiza sin borrar la fila previa.
     *
     * Se usa @Upsert en lugar de @Insert(REPLACE) porque REPLACE elimina la fila
     * antes de reinsertarla y eso dispararia el ON DELETE CASCADE de las
     * excepciones por ocurrencia (se perderian los pagos marcados).
     */
    @Upsert
    suspend fun upsert(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long>

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM expenses WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    // -------------------- Excepciones por ocurrencia --------------------

    @Query("SELECT * FROM expense_overrides WHERE occurrenceDate BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeOverrides(fromEpochDay: Long, toEpochDay: Long): Flow<List<ExpenseOverrideEntity>>

    @Query("SELECT * FROM expense_overrides")
    suspend fun getAllOverrides(): List<ExpenseOverrideEntity>

    @Query("SELECT * FROM expense_overrides WHERE expenseId = :expenseId AND occurrenceDate = :epochDay LIMIT 1")
    suspend fun getOverride(expenseId: Long, epochDay: Long): ExpenseOverrideEntity?

    @Upsert
    suspend fun upsertOverride(override: ExpenseOverrideEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverrides(overrides: List<ExpenseOverrideEntity>): List<Long>

    @Query("DELETE FROM expense_overrides WHERE expenseId = :expenseId AND occurrenceDate = :epochDay")
    suspend fun deleteOverride(expenseId: Long, epochDay: Long)

    @Query("DELETE FROM expense_overrides")
    suspend fun deleteAllOverrides()

}
