package com.qareport.generator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qareport.generator.data.local.entity.BugEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BugDao {

    @Query("SELECT * FROM bugs WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeByProject(projectId: Long): Flow<List<BugEntity>>

    @Query("SELECT * FROM bugs WHERE project_id = :projectId ORDER BY created_at DESC")
    suspend fun getByProject(projectId: Long): List<BugEntity>

    @Query("SELECT * FROM bugs WHERE id = :bugId")
    fun observeById(bugId: Long): Flow<BugEntity?>

    @Query("SELECT * FROM bugs WHERE id = :bugId")
    suspend fun getById(bugId: Long): BugEntity?

    @Query("SELECT code FROM bugs WHERE project_id = :projectId")
    suspend fun getCodes(projectId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bug: BugEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(bugs: List<BugEntity>): List<Long>

    @Update
    suspend fun update(bug: BugEntity)

    @Delete
    suspend fun delete(bug: BugEntity)

    @Query("DELETE FROM bugs WHERE id = :bugId")
    suspend fun deleteById(bugId: Long)

    @Query("UPDATE bugs SET status = :status, updated_at = :updatedAt WHERE id = :bugId")
    suspend fun updateStatus(bugId: Long, status: String, updatedAt: Long)
}
