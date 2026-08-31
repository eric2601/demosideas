package com.gastos.quincenal.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.quincenal.data.backup.BackupRepository
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.domain.model.Category
import com.gastos.quincenal.domain.model.ThemeMode
import com.gastos.quincenal.ui.components.CategoryDot
import com.gastos.quincenal.ui.components.ChipRow
import com.gastos.quincenal.ui.components.ConfirmDialog
import com.gastos.quincenal.ui.components.SectionHeader
import com.gastos.quincenal.ui.components.VerticalSpace
import java.time.YearMonth

/**
 * Configuracion: tema, moneda, aviso de sobrante bajo, categorias, duplicado de
 * meses, datos de ejemplo y respaldo en JSON mediante el selector del sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = ViewModelFactories.settings)
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var thresholdText by remember(settings.lowRemainingThreshold) {
        mutableStateOf(settings.formatter().formatPlain(settings.lowRemainingThreshold))
    }
    var symbolText by remember(settings.currencySymbol) { mutableStateOf(settings.currencySymbol) }
    var codeText by remember(settings.currencyCode) { mutableStateOf(settings.currencyCode) }

    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showEraseDialog by remember { mutableStateOf(false) }
    var showDemoDialog by remember { mutableStateOf(false) }

    // Storage Access Framework: el usuario elige donde guardar o de donde leer.
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(BackupRepository.MIME_TYPE)
    ) { uri -> uri?.let(viewModel::exportBackup) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importBackup) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Configuracion") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isBusy) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            item {
                SettingsCard(title = "Apariencia") {
                    ChipRow(
                        options = ThemeMode.entries,
                        selected = settings.themeMode,
                        label = { it.displayName },
                        onSelect = viewModel::setThemeMode
                    )
                }
            }

            item {
                SettingsCard(title = "Moneda") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = symbolText,
                            onValueChange = { symbolText = it },
                            label = { Text("Simbolo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = codeText,
                            onValueChange = { codeText = it },
                            label = { Text("Codigo") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(onClick = { viewModel.setCurrency(symbolText, codeText) }) {
                        Text("GUARDAR MONEDA")
                    }
                    Text(
                        text = "La aplicacion viene configurada para pesos mexicanos (MXN).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsCard(title = "Aviso de sobrante bajo") {
                    OutlinedTextField(
                        value = thresholdText,
                        onValueChange = { thresholdText = it },
                        label = { Text("Avisar cuando el sobrante sea menor a") },
                        prefix = { Text(settings.currencySymbol) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { viewModel.setLowRemainingThreshold(thresholdText) }) {
                        Text("GUARDAR AVISO")
                    }
                }
            }

            item {
                SectionHeader(title = "Categorias", trailing = "${categories.size} en total")
            }

            items(categories, key = { it.id }) { category ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CategoryDot(colorArgb = category.colorArgb, size = 14)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = category.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = if (category.forIncome) "Ingresos" else "Gastos",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                editingCategory = category
                                showCategoryDialog = true
                            }
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Editar categoria")
                        }
                        IconButton(onClick = { categoryToDelete = category }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar categoria")
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        editingCategory = null
                        showCategoryDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  NUEVA CATEGORIA")
                }
            }

            item {
                SettingsCard(title = "Meses") {
                    OutlinedButton(
                        onClick = { showDuplicateDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("DUPLICAR MES") }
                    Text(
                        text = "Copia los movimientos del mes actual al siguiente. " +
                            "Los gastos recurrentes que ya aparecen por su regla no se duplican.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsCard(title = "Respaldo") {
                    OutlinedButton(
                        onClick = { exportLauncher.launch(BackupRepository.suggestedFileName()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("  EXPORTAR RESPALDO")
                    }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf(BackupRepository.MIME_TYPE)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Text("  IMPORTAR RESPALDO")
                    }
                    Text(
                        text = "El respaldo es un archivo JSON que se guarda donde tu elijas. " +
                            "Al importar se reemplaza toda la informacion actual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsCard(title = "Datos de ejemplo") {
                    if (settings.demoDataLoaded) {
                        OutlinedButton(
                            onClick = viewModel::clearDemoData,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("ELIMINAR DATOS DE EJEMPLO") }
                    } else {
                        OutlinedButton(
                            onClick = { showDemoDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("CARGAR DATOS DE EJEMPLO") }
                    }
                    Text(
                        text = "Carga el mes de referencia con dos sueldos y sus gastos. " +
                            "Puedes borrarlos en cualquier momento sin afectar tus datos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SettingsCard(title = "Privacidad") {
                    Text(
                        text = "Toda la informacion se guarda unicamente en este dispositivo. " +
                            "La aplicacion no pide permiso de Internet y no envia datos a ningun servidor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    VerticalSpace(8)
                    TextButton(onClick = { showEraseDialog = true }) {
                        Text("BORRAR TODA LA INFORMACION")
                    }
                }
            }
        }
    }

    if (showCategoryDialog) {
        CategoryEditorDialog(
            category = editingCategory,
            onConfirm = { category ->
                viewModel.saveCategory(category)
                showCategoryDialog = false
                editingCategory = null
            },
            onDismiss = {
                showCategoryDialog = false
                editingCategory = null
            }
        )
    }

    categoryToDelete?.let { category ->
        ConfirmDialog(
            title = "Eliminar categoria",
            message = "Se eliminara \"${category.name}\". Los gastos que la usaban quedaran sin categoria.",
            confirmText = "Eliminar",
            onConfirm = {
                viewModel.deleteCategory(category)
                categoryToDelete = null
            },
            onDismiss = { categoryToDelete = null }
        )
    }

    if (showDuplicateDialog) {
        val source = YearMonth.now()
        DuplicateMonthDialog(
            source = source,
            target = source.plusMonths(1),
            onConfirm = { options ->
                viewModel.duplicateMonth(source, source.plusMonths(1), options)
                showDuplicateDialog = false
            },
            onDismiss = { showDuplicateDialog = false }
        )
    }

    if (showDemoDialog) {
        ConfirmDialog(
            title = "Cargar datos de ejemplo",
            message = "Se agregaran dos sueldos y sus gastos al mes actual para que veas la " +
                "aplicacion funcionando. Podras eliminarlos despues.",
            confirmText = "Cargar",
            onConfirm = {
                viewModel.loadDemoData()
                showDemoDialog = false
            },
            onDismiss = { showDemoDialog = false }
        )
    }

    if (showEraseDialog) {
        ConfirmDialog(
            title = "Borrar toda la informacion",
            message = "Se eliminaran todos los gastos, ingresos, categorias y recurrencias. " +
                "Esta accion no se puede deshacer.",
            confirmText = "Borrar todo",
            onConfirm = {
                viewModel.eraseAllData()
                showEraseDialog = false
            },
            onDismiss = { showEraseDialog = false }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader(title = title)
            content()
        }
    }
}
