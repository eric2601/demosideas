package com.qareport.generator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qareport.generator.data.local.entity.ProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY updated_at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeById(projectId: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getById(projectId: Long): ProjectEntity?

    @Query("SELECT COUNT(*) FROM projects")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteById(projectId: Long)

    @Query("UPDATE projects SET finalized = :finalized, updated_at = :updatedAt WHERE id = :projectId")
    suspend fun setFinalized(projectId: Long, finalized: Boolean, updatedAt: Long)

    @Query("UPDATE projects SET updated_at = :updatedAt WHERE id = :projectId")
    suspend fun touch(projectId: Long, updatedAt: Long)
}
