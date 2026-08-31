package com.gastos.quincenal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.ui.navigation.GastosNavHost
import com.gastos.quincenal.ui.navigation.Routes
import com.gastos.quincenal.ui.navigation.bottomDestinations
import com.gastos.quincenal.ui.settings.SettingsViewModel
import com.gastos.quincenal.ui.theme.GastosTheme

/**
 * Unica Activity de la aplicacion. La navegacion completa es Compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { GastosApp() }
    }
}

@Composable
private fun GastosApp() {
    // El tema depende de la preferencia guardada, por eso se lee antes del tema.
    val settingsViewModel: SettingsViewModel = viewModel(factory = ViewModelFactories.settings)
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

    GastosTheme(themeMode = settings.themeMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            AppScaffold()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    var showCreateSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val isTopLevel = bottomDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == destination.route } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isTopLevel) {
                FloatingActionButton(onClick = { showCreateSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Agregar movimiento"
                    )
                }
            }
        }
    ) { innerPadding ->
        GastosNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )

        if (showCreateSheet) {
            ModalBottomSheet(
                onDismissRequest = { showCreateSheet = false },
                sheetState = sheetState
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    TextButton(
                        onClick = {
                            showCreateSheet = false
                            navController.navigate(Routes.expenseEditor())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text("NUEVO GASTO", style = MaterialTheme.typography.titleMedium)
                    }
                    TextButton(
                        onClick = {
                            showCreateSheet = false
                            navController.navigate(Routes.incomeEditor())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text("NUEVO INGRESO", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
