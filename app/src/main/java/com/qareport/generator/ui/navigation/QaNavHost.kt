package com.qareport.generator.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.qareport.generator.ui.common.ObserveMessages
import com.qareport.generator.ui.screens.BugEditorScreen
import com.qareport.generator.ui.screens.BugListScreen
import com.qareport.generator.ui.screens.DashboardScreen
import com.qareport.generator.ui.screens.EvidenceScreen
import com.qareport.generator.ui.screens.ExecutionScreen
import com.qareport.generator.ui.screens.LogsScreen
import com.qareport.generator.ui.screens.ProjectDetailScreen
import com.qareport.generator.ui.screens.ProjectEditorScreen
import com.qareport.generator.ui.screens.ProjectListScreen
import com.qareport.generator.ui.screens.ReportScreen
import com.qareport.generator.ui.screens.SettingsScreen
import com.qareport.generator.ui.screens.TestCaseEditorScreen
import com.qareport.generator.ui.screens.TestCaseListScreen
import com.qareport.generator.ui.screens.TestsHubScreen
import com.qareport.generator.ui.viewmodel.BugEditorViewModel
import com.qareport.generator.ui.viewmodel.BugViewModel
import com.qareport.generator.ui.viewmodel.DashboardViewModel
import com.qareport.generator.ui.viewmodel.EvidenceViewModel
import com.qareport.generator.ui.viewmodel.LogsViewModel
import com.qareport.generator.ui.viewmodel.ProjectDetailViewModel
import com.qareport.generator.ui.viewmodel.ProjectEditorViewModel
import com.qareport.generator.ui.viewmodel.ProjectListViewModel
import com.qareport.generator.ui.viewmodel.ReportViewModel
import com.qareport.generator.ui.viewmodel.SettingsViewModel
import com.qareport.generator.ui.viewmodel.TestCaseEditorViewModel
import com.qareport.generator.ui.viewmodel.TestCaseListViewModel
import com.qareport.generator.ui.viewmodel.TestExecutionViewModel

/**
 * Grafo de navegación completo de la aplicación.
 *
 * Estructura:
 *   MainActivity
 *     ├── Dashboard
 *     ├── Projects → ProjectDetail → (TestCases, ExecuteTests, Bugs, Evidence, Logs)
 *     ├── Tests
 *     ├── Reports
 *     └── Settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QaNavHost(
    appVersion: String,
    navController: NavHostController = rememberNavController()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val bottomDestination = BottomDestination.entries.firstOrNull { it.route == currentRoute }
    val showBottomBar = bottomDestination != null
    val canNavigateBack = navController.previousBackStackEntry != null && !showBottomBar

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = titleForRoute(currentRoute, bottomDestination),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (canNavigateBack) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButtonForRoute(currentRoute, navController)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.DASHBOARD
            ) {
                composable(Routes.DASHBOARD) {
                    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
                    ObserveMessages(viewModel, snackbarHostState)
                    DashboardScreen(
                        viewModel = viewModel,
                        onCreateProject = { navController.navigate(Routes.projectEditor()) },
                        onOpenProject = { id -> navController.navigate(Routes.projectDetail(id)) },
                        onSeeAllProjects = { navController.navigate(Routes.PROJECTS) }
                    )
                }

                composable(Routes.PROJECTS) {
                    val viewModel: ProjectListViewModel = viewModel(factory = ProjectListViewModel.Factory)
                    ObserveMessages(viewModel, snackbarHostState)
                    ProjectListScreen(
                        viewModel = viewModel,
                        onOpenProject = { id -> navController.navigate(Routes.projectDetail(id)) },
                        onEditProject = { id -> navController.navigate(Routes.projectEditor(id)) },
                        onCreateProject = { navController.navigate(Routes.projectEditor()) }
                    )
                }

                composable(Routes.TESTS) {
                    val viewModel: ProjectListViewModel = viewModel(factory = ProjectListViewModel.Factory)
                    ObserveMessages(viewModel, snackbarHostState)
                    TestsHubScreen(
                        viewModel = viewModel,
                        onOpenTestCases = { id -> navController.navigate(Routes.testCases(id)) },
                        onOpenExecution = { id -> navController.navigate(Routes.execution(id)) },
                        onCreateProject = { navController.navigate(Routes.projectEditor()) }
                    )
                }

                composable(Routes.REPORTS) {
                    val viewModel: ReportViewModel = viewModel(factory = ReportViewModel.Factory)
                    ObserveMessages(viewModel, snackbarHostState)
                    ReportScreen(viewModel = viewModel)
                }

                composable(Routes.SETTINGS) {
                    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
                    ObserveMessages(viewModel, snackbarHostState)
                    SettingsScreen(viewModel = viewModel, appVersion = appVersion)
                }

                composable(
                    route = Routes.PROJECT_EDITOR_ROUTE,
                    arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType })
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val viewModel: ProjectEditorViewModel =
                        viewModel(factory = ProjectEditorViewModel.factory(projectId))
                    ObserveMessages(viewModel, snackbarHostState)
                    ProjectEditorScreen(
                        viewModel = viewModel,
                        onSaved = { id ->
                            if (projectId > 0L) {
                                navController.popBackStack()
                            } else {
                                navController.navigate(Routes.projectDetail(id)) {
                                    popUpTo(Routes.PROJECT_EDITOR_ROUTE) { inclusive = true }
                                }
                            }
                        },
                        onCancel = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.PROJECT_DETAIL_ROUTE,
                    arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType })
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val viewModel: ProjectDetailViewModel =
                        viewModel(factory = ProjectDetailViewModel.factory(projectId))
                    ObserveMessages(viewModel, snackbarHostState)
                    ProjectDetailScreen(
                        viewModel = viewModel,
                        onOpenTestCases = { navController.navigate(Routes.testCases(projectId)) },
                        onOpenExecution = { navController.navigate(Routes.execution(projectId)) },
                        onOpenBugs = { navController.navigate(Routes.bugs(projectId)) },
                        onOpenEvidence = { navController.navigate(Routes.evidence(projectId)) },
                        onOpenLogs = { navController.navigate(Routes.logs(projectId)) },
                        onReportGenerated = { navController.navigate(Routes.REPORTS) }
                    )
                }

                composable(
                    route = Routes.TEST_CASES_ROUTE,
                    arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType })
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val viewModel: TestCaseListViewModel =
                        viewModel(factory = TestCaseListViewModel.factory(projectId))
                    ObserveMessages(viewModel, snackbarHostState)
                    TestCaseListScreen(
                        viewModel = viewModel,
                        onCreateTestCase = { navController.navigate(Routes.testCaseEditor(projectId)) },
                        onEditTestCase = { id -> navController.navigate(Routes.testCaseEditor(projectId, id)) },
                        onOpenEvidence = { id -> navController.navigate(Routes.evidence(projectId, id)) }
                    )
                }

                composable(
                    route = Routes.TEST_CASE_EDITOR_ROUTE,
                    arguments = listOf(
                        navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType },
                        navArgument(Routes.ARG_TEST_CASE_ID) { type = NavType.LongType }
                    )
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val testCaseId = entry.longArg(Routes.ARG_TEST_CASE_ID)
                    val viewModel: TestCaseEditorViewModel =
                        viewModel(factory = TestCaseEditorViewModel.factory(projectId, testCaseId))
                    ObserveMessages(viewModel, snackbarHostState)
                    TestCaseEditorScreen(
                        viewModel = viewModel,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.EXECUTION_ROUTE,
                    arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType })
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val viewModel: TestExecutionViewModel =
                        viewModel(factory = TestExecutionViewModel.factory(projectId))
                    ObserveMessages(viewModel, snackbarHostState)
                    ExecutionScreen(
                        viewModel = viewModel,
                        onAddEvidence = { caseId -> navController.navigate(Routes.evidence(projectId, caseId)) },
                        onNoCases = { navController.navigate(Routes.testCases(projectId)) }
                    )
                }

                composable(
                    route = Routes.BUGS_ROUTE,
                    arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType })
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val viewModel: BugViewModel = viewModel(factory = BugViewModel.factory(projectId))
                    ObserveMessages(viewModel, snackbarHostState)
                    BugListScreen(
                        viewModel = viewModel,
                        onCreateBug = { navController.navigate(Routes.bugEditor(projectId)) },
                        onEditBug = { id -> navController.navigate(Routes.bugEditor(projectId, id)) }
                    )
                }

                composable(
                    route = Routes.BUG_EDITOR_ROUTE,
                    arguments = listOf(
                        navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType },
                        navArgument(Routes.ARG_BUG_ID) { type = NavType.LongType }
                    )
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val bugId = entry.longArg(Routes.ARG_BUG_ID)
                    val viewModel: BugEditorViewModel =
                        viewModel(factory = BugEditorViewModel.factory(projectId, bugId))
                    ObserveMessages(viewModel, snackbarHostState)
                    BugEditorScreen(
                        viewModel = viewModel,
                        onSaved = { navController.popBackStack() },
                        onCancel = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Routes.EVIDENCE_ROUTE,
                    arguments = listOf(
                        navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType },
                        navArgument(Routes.ARG_TEST_CASE_ID) { type = NavType.LongType }
                    )
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val testCaseId = entry.longArg(Routes.ARG_TEST_CASE_ID)
                    val viewModel: EvidenceViewModel =
                        viewModel(factory = EvidenceViewModel.factory(projectId, testCaseId))
                    ObserveMessages(viewModel, snackbarHostState)
                    EvidenceScreen(viewModel = viewModel)
                }

                composable(
                    route = Routes.LOGS_ROUTE,
                    arguments = listOf(navArgument(Routes.ARG_PROJECT_ID) { type = NavType.LongType })
                ) { entry ->
                    val projectId = entry.longArg(Routes.ARG_PROJECT_ID)
                    val viewModel: LogsViewModel = viewModel(factory = LogsViewModel.factory(projectId))
                    ObserveMessages(viewModel, snackbarHostState)
                    LogsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

/** FAB contextual: solo aparece en las pantallas donde crear algo tiene sentido. */
@Composable
private fun FloatingActionButtonForRoute(currentRoute: String?, navController: NavHostController) {
    val entry = navController.currentBackStackEntry
    when (currentRoute) {
        Routes.PROJECTS -> FloatingActionButton(
            onClick = { navController.navigate(Routes.projectEditor()) }
        ) { Icon(Icons.Filled.Add, contentDescription = "Nuevo proyecto") }

        Routes.TEST_CASES_ROUTE -> {
            val projectId = entry?.arguments?.getLong(Routes.ARG_PROJECT_ID) ?: 0L
            FloatingActionButton(
                onClick = { navController.navigate(Routes.testCaseEditor(projectId)) }
            ) { Icon(Icons.Filled.Add, contentDescription = "Nuevo caso de prueba") }
        }

        Routes.BUGS_ROUTE -> {
            val projectId = entry?.arguments?.getLong(Routes.ARG_PROJECT_ID) ?: 0L
            FloatingActionButton(
                onClick = { navController.navigate(Routes.bugEditor(projectId)) }
            ) { Icon(Icons.Filled.Add, contentDescription = "Nuevo defecto") }
        }

        else -> Unit
    }
}

private fun titleForRoute(route: String?, bottomDestination: BottomDestination?): String {
    if (bottomDestination != null) {
        return if (bottomDestination == BottomDestination.HOME) "QA Report Generator" else bottomDestination.label
    }
    return when (route) {
        Routes.PROJECT_EDITOR_ROUTE -> "Proyecto"
        Routes.PROJECT_DETAIL_ROUTE -> "Detalle del proyecto"
        Routes.TEST_CASES_ROUTE -> "Casos de prueba"
        Routes.TEST_CASE_EDITOR_ROUTE -> "Caso de prueba"
        Routes.EXECUTION_ROUTE -> "Ejecución de pruebas"
        Routes.BUGS_ROUTE -> "Defectos"
        Routes.BUG_EDITOR_ROUTE -> "Defecto"
        Routes.EVIDENCE_ROUTE -> "Evidencias"
        Routes.LOGS_ROUTE -> "Logs"
        else -> "QA Report Generator"
    }
}

/** Lectura segura de un argumento Long de la ruta. */
private fun androidx.navigation.NavBackStackEntry.longArg(key: String): Long =
    arguments?.getLong(key) ?: 0L
