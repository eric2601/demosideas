package com.qareport.generator.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qareport.generator.data.local.entity.TestExecutionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TestExecutionDao {

    @Query("SELECT * FROM test_executions WHERE project_id = :projectId ORDER BY executed_at ASC")
    fun observeByProject(projectId: Long): Flow<List<TestExecutionEntity>>

    @Query("SELECT * FROM test_executions WHERE project_id = :projectId ORDER BY executed_at ASC")
    suspend fun getByProject(projectId: Long): List<TestExecutionEntity>

    @Query("SELECT * FROM test_executions WHERE test_case_id = :testCaseId ORDER BY executed_at DESC")
    fun observeByTestCase(testCaseId: Long): Flow<List<TestExecutionEntity>>

    @Query("SELECT * FROM test_executions WHERE test_case_id = :testCaseId ORDER BY executed_at DESC LIMIT 1")
    suspend fun getLastForTestCase(testCaseId: Long): TestExecutionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(execution: TestExecutionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(executions: List<TestExecutionEntity>): List<Long>

    @Query("DELETE FROM test_executions WHERE project_id = :projectId")
    suspend fun deleteByProject(projectId: Long)
}
