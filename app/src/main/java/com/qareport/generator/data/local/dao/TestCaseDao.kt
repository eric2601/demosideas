package com.qareport.generator.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.qareport.generator.data.local.entity.TestCaseEntity
import kotlinx.coroutines.flow.Flow

/** Fila agregada usada por el Dashboard global (una sola consulta en vez de cuatro). */
data class GlobalCountsRow(
    val executed: Int,
    val passed: Int,
    val failed: Int
)

@Dao
interface TestCaseDao {

    @Query("SELECT * FROM test_cases WHERE project_id = :projectId ORDER BY order_index ASC, id ASC")
    fun observeByProject(projectId: Long): Flow<List<TestCaseEntity>>

    @Query("SELECT * FROM test_cases WHERE project_id = :projectId ORDER BY order_index ASC, id ASC")
    suspend fun getByProject(projectId: Long): List<TestCaseEntity>

    @Query("SELECT * FROM test_cases WHERE id = :testCaseId")
    fun observeById(testCaseId: Long): Flow<TestCaseEntity?>

    @Query("SELECT * FROM test_cases WHERE id = :testCaseId")
    suspend fun getById(testCaseId: Long): TestCaseEntity?

    @Query("SELECT code FROM test_cases WHERE project_id = :projectId")
    suspend fun getCodes(projectId: Long): List<String>

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM test_cases WHERE project_id = :projectId")
    suspend fun getMaxOrderIndex(projectId: Long): Int

    @Query(
        """
        SELECT
            COALESCE(SUM(CASE WHEN status <> 'NOT_TESTED' THEN 1 ELSE 0 END), 0) AS executed,
            COALESCE(SUM(CASE WHEN status = 'PASS' THEN 1 ELSE 0 END), 0) AS passed,
            COALESCE(SUM(CASE WHEN status = 'FAIL' THEN 1 ELSE 0 END), 0) AS failed
        FROM test_cases
        """
    )
    fun observeGlobalCounts(): Flow<GlobalCountsRow?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(testCase: TestCaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(testCases: List<TestCaseEntity>): List<Long>

    @Update
    suspend fun update(testCase: TestCaseEntity)

    @Delete
    suspend fun delete(testCase: TestCaseEntity)

    @Query("DELETE FROM test_cases WHERE id = :testCaseId")
    suspend fun deleteById(testCaseId: Long)

    @Query(
        """
        UPDATE test_cases
        SET status = :status, severity = :severity, actual_result = :actualResult,
            comments = :comments, updated_at = :updatedAt
        WHERE id = :testCaseId
        """
    )
    suspend fun updateResult(
        testCaseId: Long,
        status: String,
        severity: String?,
        actualResult: String,
        comments: String,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE test_cases
        SET status = 'NOT_TESTED', severity = NULL, actual_result = '', updated_at = :updatedAt
        WHERE project_id = :projectId
        """
    )
    suspend fun resetResults(projectId: Long, updatedAt: Long)
}
