package com.qareport.generator.domain.export

import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Evidence
import com.qareport.generator.domain.model.EvidenceType
import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.ProjectBundle
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestExecution
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.stats.StatsCalculator

/**
 * Exporta e importa un proyecto QA completo en JSON.
 *
 * El formato es el respaldo oficial de la aplicación y hace round-trip:
 * `import(export(bundle))` devuelve exactamente los mismos datos.
 */
object ProjectJsonExporter {

    const val SCHEMA_VERSION = 1

    fun export(bundle: ProjectBundle): String = Json.stringify(toJson(bundle))

    fun toJson(bundle: ProjectBundle): JsonValue.Obj {
        val stats = StatsCalculator.compute(bundle.testCases, bundle.bugs)
        return Json.obj(
            "schemaVersion" to Json.of(SCHEMA_VERSION),
            "generatedAt" to Json.of(System.currentTimeMillis()),
            "project" to projectToJson(bundle.project),
            "summary" to Json.obj(
                "total" to Json.of(stats.total),
                "pass" to Json.of(stats.passed),
                "fail" to Json.of(stats.failed),
                "blocked" to Json.of(stats.blocked),
                "skipped" to Json.of(stats.skipped),
                "notTested" to Json.of(stats.notTested),
                "passRate" to Json.of(stats.passRate),
                "bugsTotal" to Json.of(stats.bugsTotal)
            ),
            "testCases" to Json.arr(bundle.testCases.map(::testCaseToJson)),
            "executions" to Json.arr(bundle.executions.map(::executionToJson)),
            "bugs" to Json.arr(bundle.bugs.map(::bugToJson)),
            "evidence" to Json.arr(bundle.evidences.map(::evidenceToJson)),
            "logs" to Json.arr(bundle.logs.map(::logToJson))
        )
    }

    /** Devuelve null si el texto no es un respaldo válido de esta aplicación. */
    fun import(text: String): ProjectBundle? {
        val root = JsonParser.parseObjectOrNull(text) ?: return null
        val projectJson = root["project"] as? JsonValue.Obj ?: return null
        val project = projectFromJson(projectJson)
        if (project.name.isBlank()) return null

        return ProjectBundle(
            project = project,
            testCases = Json.objects(root["testCases"]).map(::testCaseFromJson),
            executions = Json.objects(root["executions"]).map(::executionFromJson),
            bugs = Json.objects(root["bugs"]).map(::bugFromJson),
            evidences = Json.objects(root["evidence"]).map(::evidenceFromJson),
            logs = Json.objects(root["logs"]).map(::logFromJson)
        )
    }

    // --- proyecto ---

    private fun projectToJson(project: Project): JsonValue.Obj = Json.obj(
        "id" to Json.of(project.id),
        "name" to Json.of(project.name),
        "client" to Json.of(project.client),
        "product" to Json.of(project.product),
        "version" to Json.of(project.version),
        "build" to Json.of(project.buildNumber),
        "deviceType" to Json.of(project.deviceType),
        "deviceModel" to Json.of(project.deviceModel),
        "operatingSystem" to Json.of(project.operatingSystem),
        "androidVersion" to Json.of(project.androidVersion),
        "tester" to Json.of(project.tester),
        "environment" to Json.of(project.environment),
        "startDate" to Json.ofNullable(project.startDate),
        "endDate" to Json.ofNullable(project.endDate),
        "description" to Json.of(project.description),
        "notes" to Json.of(project.notes),
        "finalized" to Json.of(project.finalized),
        "createdAt" to Json.of(project.createdAt),
        "updatedAt" to Json.of(project.updatedAt)
    )

    private fun projectFromJson(json: JsonValue.Obj): Project = Project(
        id = Json.long(json["id"]),
        name = Json.string(json["name"]),
        client = Json.string(json["client"]),
        product = Json.string(json["product"]),
        version = Json.string(json["version"]),
        buildNumber = Json.string(json["build"]),
        deviceType = Json.string(json["deviceType"]),
        deviceModel = Json.string(json["deviceModel"]),
        operatingSystem = Json.string(json["operatingSystem"]),
        androidVersion = Json.string(json["androidVersion"]),
        tester = Json.string(json["tester"]),
        environment = Json.string(json["environment"]),
        startDate = Json.longOrNull(json["startDate"]),
        endDate = Json.longOrNull(json["endDate"]),
        description = Json.string(json["description"]),
        notes = Json.string(json["notes"]),
        finalized = Json.bool(json["finalized"]),
        createdAt = Json.long(json["createdAt"]),
        updatedAt = Json.long(json["updatedAt"])
    )

    // --- casos de prueba ---

    private fun testCaseToJson(testCase: TestCase): JsonValue.Obj = Json.obj(
        "id" to Json.of(testCase.id),
        "projectId" to Json.of(testCase.projectId),
        "code" to Json.of(testCase.code),
        "title" to Json.of(testCase.title),
        "module" to Json.of(testCase.module),
        "priority" to Json.of(testCase.priority.key),
        "preconditions" to Json.of(testCase.preconditions),
        "steps" to Json.of(testCase.steps),
        "expectedResult" to Json.of(testCase.expectedResult),
        "actualResult" to Json.of(testCase.actualResult),
        "status" to Json.of(testCase.status.key),
        "severity" to Json.of(testCase.severity?.key),
        "comments" to Json.of(testCase.comments),
        "orderIndex" to Json.of(testCase.orderIndex),
        "createdAt" to Json.of(testCase.createdAt),
        "updatedAt" to Json.of(testCase.updatedAt)
    )

    private fun testCaseFromJson(json: JsonValue.Obj): TestCase = TestCase(
        id = Json.long(json["id"]),
        projectId = Json.long(json["projectId"]),
        code = Json.string(json["code"]),
        title = Json.string(json["title"]),
        module = Json.string(json["module"]),
        priority = Priority.from(Json.string(json["priority"])),
        preconditions = Json.string(json["preconditions"]),
        steps = Json.string(json["steps"]),
        expectedResult = Json.string(json["expectedResult"]),
        actualResult = Json.string(json["actualResult"]),
        status = TestStatus.from(Json.string(json["status"])),
        severity = Severity.fromOrNull(Json.string(json["severity"])),
        comments = Json.string(json["comments"]),
        orderIndex = Json.int(json["orderIndex"]),
        createdAt = Json.long(json["createdAt"]),
        updatedAt = Json.long(json["updatedAt"])
    )

    // --- ejecuciones ---

    private fun executionToJson(execution: TestExecution): JsonValue.Obj = Json.obj(
        "id" to Json.of(execution.id),
        "projectId" to Json.of(execution.projectId),
        "testCaseId" to Json.of(execution.testCaseId),
        "status" to Json.of(execution.status.key),
        "executedAt" to Json.of(execution.executedAt),
        "executedBy" to Json.of(execution.executedBy),
        "notes" to Json.of(execution.notes),
        "problemDescription" to Json.of(execution.problemDescription),
        "severity" to Json.of(execution.severity?.key),
        "stepsToReproduce" to Json.of(execution.stepsToReproduce),
        "expectedResult" to Json.of(execution.expectedResult),
        "actualResult" to Json.of(execution.actualResult)
    )

    private fun executionFromJson(json: JsonValue.Obj): TestExecution = TestExecution(
        id = Json.long(json["id"]),
        projectId = Json.long(json["projectId"]),
        testCaseId = Json.long(json["testCaseId"]),
        status = TestStatus.from(Json.string(json["status"])),
        executedAt = Json.long(json["executedAt"]),
        executedBy = Json.string(json["executedBy"]),
        notes = Json.string(json["notes"]),
        problemDescription = Json.string(json["problemDescription"]),
        severity = Severity.fromOrNull(Json.string(json["severity"])),
        stepsToReproduce = Json.string(json["stepsToReproduce"]),
        expectedResult = Json.string(json["expectedResult"]),
        actualResult = Json.string(json["actualResult"])
    )

    // --- defectos ---

    private fun bugToJson(bug: Bug): JsonValue.Obj = Json.obj(
        "id" to Json.of(bug.id),
        "projectId" to Json.of(bug.projectId),
        "testCaseId" to Json.ofNullable(bug.testCaseId),
        "code" to Json.of(bug.code),
        "title" to Json.of(bug.title),
        "description" to Json.of(bug.description),
        "severity" to Json.of(bug.severity.key),
        "priority" to Json.of(bug.priority.key),
        "stepsToReproduce" to Json.of(bug.stepsToReproduce),
        "expectedResult" to Json.of(bug.expectedResult),
        "actualResult" to Json.of(bug.actualResult),
        "version" to Json.of(bug.version),
        "device" to Json.of(bug.device),
        "status" to Json.of(bug.status.key),
        "createdAt" to Json.of(bug.createdAt),
        "updatedAt" to Json.of(bug.updatedAt)
    )

    private fun bugFromJson(json: JsonValue.Obj): Bug = Bug(
        id = Json.long(json["id"]),
        projectId = Json.long(json["projectId"]),
        testCaseId = Json.longOrNull(json["testCaseId"]),
        code = Json.string(json["code"]),
        title = Json.string(json["title"]),
        description = Json.string(json["description"]),
        severity = Severity.from(Json.string(json["severity"])),
        priority = Priority.from(Json.string(json["priority"])),
        stepsToReproduce = Json.string(json["stepsToReproduce"]),
        expectedResult = Json.string(json["expectedResult"]),
        actualResult = Json.string(json["actualResult"]),
        version = Json.string(json["version"]),
        device = Json.string(json["device"]),
        status = BugStatus.from(Json.string(json["status"])),
        createdAt = Json.long(json["createdAt"]),
        updatedAt = Json.long(json["updatedAt"])
    )

    // --- evidencias ---

    private fun evidenceToJson(evidence: Evidence): JsonValue.Obj = Json.obj(
        "id" to Json.of(evidence.id),
        "projectId" to Json.of(evidence.projectId),
        "testCaseId" to Json.ofNullable(evidence.testCaseId),
        "bugId" to Json.ofNullable(evidence.bugId),
        "displayName" to Json.of(evidence.displayName),
        "uri" to Json.of(evidence.uri),
        "type" to Json.of(evidence.type.key),
        "mimeType" to Json.of(evidence.mimeType),
        "sizeBytes" to Json.of(evidence.sizeBytes),
        "note" to Json.of(evidence.note),
        "createdAt" to Json.of(evidence.createdAt)
    )

    private fun evidenceFromJson(json: JsonValue.Obj): Evidence = Evidence(
        id = Json.long(json["id"]),
        projectId = Json.long(json["projectId"]),
        testCaseId = Json.longOrNull(json["testCaseId"]),
        bugId = Json.longOrNull(json["bugId"]),
        displayName = Json.string(json["displayName"]),
        uri = Json.string(json["uri"]),
        type = EvidenceType.from(Json.string(json["type"])),
        mimeType = Json.string(json["mimeType"]),
        sizeBytes = Json.long(json["sizeBytes"]),
        note = Json.string(json["note"]),
        createdAt = Json.long(json["createdAt"])
    )

    // --- logs ---

    private fun logToJson(log: LogEntry): JsonValue.Obj = Json.obj(
        "id" to Json.of(log.id),
        "projectId" to Json.of(log.projectId),
        "testCaseId" to Json.ofNullable(log.testCaseId),
        "title" to Json.of(log.title),
        "content" to Json.of(log.content),
        "createdAt" to Json.of(log.createdAt)
    )

    private fun logFromJson(json: JsonValue.Obj): LogEntry = LogEntry(
        id = Json.long(json["id"]),
        projectId = Json.long(json["projectId"]),
        testCaseId = Json.longOrNull(json["testCaseId"]),
        title = Json.string(json["title"]),
        content = Json.string(json["content"]),
        createdAt = Json.long(json["createdAt"])
    )
}
