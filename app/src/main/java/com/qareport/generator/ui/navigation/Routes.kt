package com.qareport.generator.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Rutas de navegación.
 *
 * Se usan rutas con argumentos en la URL (navigation-compose clásico) en lugar de las
 * rutas tipadas de Navigation 2.8, para no depender de kotlinx-serialization.
 */
object Routes {

    const val ARG_PROJECT_ID = "projectId"
    const val ARG_TEST_CASE_ID = "testCaseId"
    const val ARG_BUG_ID = "bugId"

    const val DASHBOARD = "dashboard"
    const val PROJECTS = "projects"
    const val TESTS = "tests"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    private const val PROJECT_EDITOR_BASE = "project_editor"
    private const val PROJECT_DETAIL_BASE = "project_detail"
    private const val TEST_CASES_BASE = "test_cases"
    private const val TEST_CASE_EDITOR_BASE = "test_case_editor"
    private const val EXECUTION_BASE = "execution"
    private const val BUGS_BASE = "bugs"
    private const val BUG_EDITOR_BASE = "bug_editor"
    private const val EVIDENCE_BASE = "evidence"
    private const val LOGS_BASE = "logs"

    const val PROJECT_EDITOR_ROUTE = "$PROJECT_EDITOR_BASE/{$ARG_PROJECT_ID}"
    const val PROJECT_DETAIL_ROUTE = "$PROJECT_DETAIL_BASE/{$ARG_PROJECT_ID}"
    const val TEST_CASES_ROUTE = "$TEST_CASES_BASE/{$ARG_PROJECT_ID}"
    const val TEST_CASE_EDITOR_ROUTE = "$TEST_CASE_EDITOR_BASE/{$ARG_PROJECT_ID}/{$ARG_TEST_CASE_ID}"
    const val EXECUTION_ROUTE = "$EXECUTION_BASE/{$ARG_PROJECT_ID}"
    const val BUGS_ROUTE = "$BUGS_BASE/{$ARG_PROJECT_ID}"
    const val BUG_EDITOR_ROUTE = "$BUG_EDITOR_BASE/{$ARG_PROJECT_ID}/{$ARG_BUG_ID}"
    const val EVIDENCE_ROUTE = "$EVIDENCE_BASE/{$ARG_PROJECT_ID}/{$ARG_TEST_CASE_ID}"
    const val LOGS_ROUTE = "$LOGS_BASE/{$ARG_PROJECT_ID}"

    /** 0 significa "nuevo proyecto". */
    fun projectEditor(projectId: Long = 0L) = "$PROJECT_EDITOR_BASE/$projectId"

    fun projectDetail(projectId: Long) = "$PROJECT_DETAIL_BASE/$projectId"

    fun testCases(projectId: Long) = "$TEST_CASES_BASE/$projectId"

    /** 0 en [testCaseId] significa "nuevo caso". */
    fun testCaseEditor(projectId: Long, testCaseId: Long = 0L) =
        "$TEST_CASE_EDITOR_BASE/$projectId/$testCaseId"

    fun execution(projectId: Long) = "$EXECUTION_BASE/$projectId"

    fun bugs(projectId: Long) = "$BUGS_BASE/$projectId"

    fun bugEditor(projectId: Long, bugId: Long = 0L) = "$BUG_EDITOR_BASE/$projectId/$bugId"

    /** 0 en [testCaseId] muestra las evidencias de todo el proyecto. */
    fun evidence(projectId: Long, testCaseId: Long = 0L) = "$EVIDENCE_BASE/$projectId/$testCaseId"

    fun logs(projectId: Long) = "$LOGS_BASE/$projectId"
}

/** Destinos del menú inferior. */
enum class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    HOME(Routes.DASHBOARD, "Inicio", Icons.Filled.Home),
    PROJECTS(Routes.PROJECTS, "Proyectos", Icons.Filled.Folder),
    TESTS(Routes.TESTS, "Pruebas", Icons.Filled.PlaylistAddCheck),
    REPORTS(Routes.REPORTS, "Reportes", Icons.Outlined.Description),
    SETTINGS(Routes.SETTINGS, "Configuración", Icons.Filled.Settings)
}
