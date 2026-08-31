package com.gastos.quincenal.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.gastos.quincenal.data.local.entity.RecurrenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurrenceDao {

    @Query("SELECT * FROM recurrences")
    fun observeAll(): Flow<List<RecurrenceEntity>>

    @Query("SELECT * FROM recurrences")
    suspend fun getAll(): List<RecurrenceEntity>

    @Query("SELECT * FROM recurrences WHERE id = :id")
    suspend fun getById(id: Long): RecurrenceEntity?

    @Upsert
    suspend fun upsert(recurrence: RecurrenceEntity): Long

    @Insert
    suspend fun insert(recurrence: RecurrenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recurrences: List<RecurrenceEntity>): List<Long>

    @Update
    suspend fun update(recurrence: RecurrenceEntity)

    @Query("DELETE FROM recurrences WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recurrences")
    suspend fun deleteAll()
}
