package com.qareport.generator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qareport.generator.data.local.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {

    @Query("SELECT * FROM log_entries WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeByProject(projectId: Long): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE project_id = :projectId ORDER BY created_at DESC")
    suspend fun getByProject(projectId: Long): List<LogEntryEntity>

    @Query("SELECT * FROM log_entries WHERE id = :logId")
    suspend fun getById(logId: Long): LogEntryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: LogEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<LogEntryEntity>): List<Long>

    @Update
    suspend fun update(entry: LogEntryEntity)

    @Delete
    suspend fun delete(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE id = :logId")
    suspend fun deleteById(logId: Long)
}
