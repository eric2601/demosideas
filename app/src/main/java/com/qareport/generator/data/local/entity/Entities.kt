package com.qareport.generator.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidades de Room.
 *
 * Jerarquía y claves foráneas:
 *
 *   ProjectEntity
 *      ├── TestCaseEntity      (CASCADE)
 *      │       ├── TestExecutionEntity (CASCADE)
 *      │       └── EvidenceEntity      (SET NULL: la evidencia sobrevive al caso)
 *      ├── BugEntity           (CASCADE)
 *      ├── EvidenceEntity      (CASCADE)
 *      ├── LogEntryEntity      (CASCADE)
 *      └── ReportEntity        (CASCADE)
 *
 * IMPORTANTE: las evidencias NUNCA guardan el binario del archivo. Solo se persiste
 * la URI (content:// o ruta interna) junto con sus metadatos.
 */

@Entity(
    tableName = "projects",
    indices = [Index(value = ["name"])]
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val client: String = "",
    val product: String = "",
    val version: String = "",
    @ColumnInfo(name = "build_number")
    val buildNumber: String = "",
    @ColumnInfo(name = "device_type")
    val deviceType: String = "",
    @ColumnInfo(name = "device_model")
    val deviceModel: String = "",
    @ColumnInfo(name = "operating_system")
    val operatingSystem: String = "",
    @ColumnInfo(name = "android_version")
    val androidVersion: String = "",
    val tester: String = "",
    val environment: String = "",
    @ColumnInfo(name = "start_date")
    val startDate: Long? = null,
    @ColumnInfo(name = "end_date")
    val endDate: Long? = null,
    val description: String = "",
    val notes: String = "",
    val finalized: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "test_cases",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["project_id", "code"], unique = true)
    ]
)
data class TestCaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    val code: String,
    val title: String,
    val module: String = "",
    val priority: String,
    val preconditions: String = "",
    val steps: String = "",
    @ColumnInfo(name = "expected_result")
    val expectedResult: String = "",
    @ColumnInfo(name = "actual_result")
    val actualResult: String = "",
    val status: String,
    val severity: String? = null,
    val comments: String = "",
    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "test_executions",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TestCaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["test_case_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["project_id"]), Index(value = ["test_case_id"])]
)
data class TestExecutionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "test_case_id")
    val testCaseId: Long,
    val status: String,
    @ColumnInfo(name = "executed_at")
    val executedAt: Long,
    @ColumnInfo(name = "executed_by")
    val executedBy: String = "",
    val notes: String = "",
    @ColumnInfo(name = "problem_description")
    val problemDescription: String = "",
    val severity: String? = null,
    @ColumnInfo(name = "steps_to_reproduce")
    val stepsToReproduce: String = "",
    @ColumnInfo(name = "expected_result")
    val expectedResult: String = "",
    @ColumnInfo(name = "actual_result")
    val actualResult: String = ""
)

@Entity(
    tableName = "bugs",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TestCaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["test_case_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["test_case_id"]),
        Index(value = ["project_id", "code"], unique = true)
    ]
)
data class BugEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "test_case_id")
    val testCaseId: Long? = null,
    val code: String,
    val title: String,
    val description: String = "",
    val severity: String,
    val priority: String,
    @ColumnInfo(name = "steps_to_reproduce")
    val stepsToReproduce: String = "",
    @ColumnInfo(name = "expected_result")
    val expectedResult: String = "",
    @ColumnInfo(name = "actual_result")
    val actualResult: String = "",
    val version: String = "",
    val device: String = "",
    val status: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "evidences",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TestCaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["test_case_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BugEntity::class,
            parentColumns = ["id"],
            childColumns = ["bug_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["test_case_id"]),
        Index(value = ["bug_id"])
    ]
)
data class EvidenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "test_case_id")
    val testCaseId: Long? = null,
    @ColumnInfo(name = "bug_id")
    val bugId: Long? = null,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    /** Solo la referencia al archivo: nunca su contenido binario. */
    val uri: String,
    val type: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "",
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0L,
    val note: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "log_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TestCaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["test_case_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["project_id"]), Index(value = ["test_case_id"])]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "test_case_id")
    val testCaseId: Long? = null,
    val title: String,
    val content: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["project_id"])]
)
data class ReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    @ColumnInfo(name = "file_name")
    val fileName: String,
    @ColumnInfo(name = "file_path")
    val filePath: String,
    val format: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long = 0L,
    @ColumnInfo(name = "total_tests")
    val totalTests: Int = 0,
    @ColumnInfo(name = "pass_rate")
    val passRate: Double = 0.0
)
