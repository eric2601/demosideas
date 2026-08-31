package com.qareport.generator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qareport.generator.data.local.entity.ReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Query("SELECT * FROM reports ORDER BY created_at DESC")
    fun observeAll(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeByProject(projectId: Long): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE id = :reportId")
    suspend fun getById(reportId: Long): ReportEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(report: ReportEntity): Long

    @Delete
    suspend fun delete(report: ReportEntity)

    @Query("DELETE FROM reports WHERE id = :reportId")
    suspend fun deleteById(reportId: Long)

    @Query("UPDATE reports SET file_name = :fileName, file_path = :filePath WHERE id = :reportId")
    suspend fun rename(reportId: Long, fileName: String, filePath: String)
}
