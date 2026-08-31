package com.qareport.generator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qareport.generator.data.local.entity.EvidenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceDao {

    @Query("SELECT * FROM evidences WHERE project_id = :projectId ORDER BY created_at DESC")
    fun observeByProject(projectId: Long): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidences WHERE project_id = :projectId ORDER BY created_at DESC")
    suspend fun getByProject(projectId: Long): List<EvidenceEntity>

    @Query("SELECT * FROM evidences WHERE test_case_id = :testCaseId ORDER BY created_at DESC")
    fun observeByTestCase(testCaseId: Long): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidences WHERE bug_id = :bugId ORDER BY created_at DESC")
    fun observeByBug(bugId: Long): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidences WHERE id = :evidenceId")
    suspend fun getById(evidenceId: Long): EvidenceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(evidence: EvidenceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(evidences: List<EvidenceEntity>): List<Long>

    @Delete
    suspend fun delete(evidence: EvidenceEntity)

    @Query("DELETE FROM evidences WHERE id = :evidenceId")
    suspend fun deleteById(evidenceId: Long)
}
