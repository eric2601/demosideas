package com.qareport.generator.data.mapper

import com.qareport.generator.data.local.entity.BugEntity
import com.qareport.generator.data.local.entity.EvidenceEntity
import com.qareport.generator.data.local.entity.LogEntryEntity
import com.qareport.generator.data.local.entity.ProjectEntity
import com.qareport.generator.data.local.entity.ReportEntity
import com.qareport.generator.data.local.entity.TestCaseEntity
import com.qareport.generator.data.local.entity.TestExecutionEntity
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Evidence
import com.qareport.generator.domain.model.EvidenceType
import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.Report
import com.qareport.generator.domain.model.ReportFormat
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestExecution
import com.qareport.generator.domain.model.TestStatus

/**
 * Conversión entre entidades de Room y modelos de dominio.
 *
 * Los enums se persisten como texto y se leen con los parsers tolerantes de cada enum,
 * de modo que un valor corrupto en la base de datos degrada a un valor por defecto en
 * lugar de provocar una excepción al abrir la pantalla.
 */

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    client = client,
    product = product,
    version = version,
    buildNumber = buildNumber,
    deviceType = deviceType,
    deviceModel = deviceModel,
    operatingSystem = operatingSystem,
    androidVersion = androidVersion,
    tester = tester,
    environment = environment,
    startDate = startDate,
    endDate = endDate,
    description = description,
    notes = notes,
    finalized = finalized,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    client = client,
    product = product,
    version = version,
    buildNumber = buildNumber,
    deviceType = deviceType,
    deviceModel = deviceModel,
    operatingSystem = operatingSystem,
    androidVersion = androidVersion,
    tester = tester,
    environment = environment,
    startDate = startDate,
    endDate = endDate,
    description = description,
    notes = notes,
    finalized = finalized,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TestCaseEntity.toDomain(): TestCase = TestCase(
    id = id,
    projectId = projectId,
    code = code,
    title = title,
    module = module,
    priority = Priority.from(priority),
    preconditions = preconditions,
    steps = steps,
    expectedResult = expectedResult,
    actualResult = actualResult,
    status = TestStatus.from(status),
    severity = Severity.fromOrNull(severity),
    comments = comments,
    orderIndex = orderIndex,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TestCase.toEntity(): TestCaseEntity = TestCaseEntity(
    id = id,
    projectId = projectId,
    code = code,
    title = title,
    module = module,
    priority = priority.key,
    preconditions = preconditions,
    steps = steps,
    expectedResult = expectedResult,
    actualResult = actualResult,
    status = status.key,
    severity = severity?.key,
    comments = comments,
    orderIndex = orderIndex,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TestExecutionEntity.toDomain(): TestExecution = TestExecution(
    id = id,
    projectId = projectId,
    testCaseId = testCaseId,
    status = TestStatus.from(status),
    executedAt = executedAt,
    executedBy = executedBy,
    notes = notes,
    problemDescription = problemDescription,
    severity = Severity.fromOrNull(severity),
    stepsToReproduce = stepsToReproduce,
    expectedResult = expectedResult,
    actualResult = actualResult
)

fun TestExecution.toEntity(): TestExecutionEntity = TestExecutionEntity(
    id = id,
    projectId = projectId,
    testCaseId = testCaseId,
    status = status.key,
    executedAt = executedAt,
    executedBy = executedBy,
    notes = notes,
    problemDescription = problemDescription,
    severity = severity?.key,
    stepsToReproduce = stepsToReproduce,
    expectedResult = expectedResult,
    actualResult = actualResult
)

fun BugEntity.toDomain(): Bug = Bug(
    id = id,
    projectId = projectId,
    testCaseId = testCaseId,
    code = code,
    title = title,
    description = description,
    severity = Severity.from(severity),
    priority = Priority.from(priority),
    stepsToReproduce = stepsToReproduce,
    expectedResult = expectedResult,
    actualResult = actualResult,
    version = version,
    device = device,
    status = BugStatus.from(status),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Bug.toEntity(): BugEntity = BugEntity(
    id = id,
    projectId = projectId,
    testCaseId = testCaseId,
    code = code,
    title = title,
    description = description,
    severity = severity.key,
    priority = priority.key,
    stepsToReproduce = stepsToReproduce,
    expectedResult = expectedResult,
    actualResult = actualResult,
    version = version,
    device = device,
    status = status.key,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun EvidenceEntity.toDomain(): Evidence = Evidence(
    id = id,
    projectId = projectId,
    testCaseId = testCaseId,
    bugId = bugId,
    displayName = displayName,
    uri = uri,
    type = EvidenceType.from(type),
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    note = note,
    createdAt = createdAt
)

fun Evidence.toEntity(): EvidenceEntity = EvidenceEntity(
    id = id,
    projectId = projectId,
    testCaseId = testCaseId,
    bugId = bugId,
    displayName = displayName,
    uri = uri,
    type = type.key,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    note = note,
    createdAt = createdAt
)

fun LogEntryEntity.toDomain(): LogEntry = LogEntry(
    id = id,
    projectId = projectId,
    testCaseId = testCaseId,
    title = title,
    content = content,
    createdAt = createdAt
)

fun LogEntry.toEntity(): LogEntryEntity = LogEntryEntity(
    id = id,
    projectId = projectId,
    testCaseId = testCaseId,
    title = title,
    content = content,
    createdAt = createdAt
)

fun ReportEntity.toDomain(): Report = Report(
    id = id,
    projectId = projectId,
    fileName = fileName,
    filePath = filePath,
    format = ReportFormat.from(format),
    createdAt = createdAt,
    sizeBytes = sizeBytes,
    totalTests = totalTests,
    passRate = passRate
)

fun Report.toEntity(): ReportEntity = ReportEntity(
    id = id,
    projectId = projectId,
    fileName = fileName,
    filePath = filePath,
    format = format.key,
    createdAt = createdAt,
    sizeBytes = sizeBytes,
    totalTests = totalTests,
    passRate = passRate
)
