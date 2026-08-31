package com.gastos.quincenal.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.domain.model.IncomeOccurrence
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.ui.components.ConfirmDialog
import com.gastos.quincenal.ui.components.EmptyState
import com.gastos.quincenal.ui.components.IncomeRow
import com.gastos.quincenal.ui.components.LabeledAmountRow
import com.gastos.quincenal.ui.components.MonthNavigator
import com.gastos.quincenal.ui.theme.quincenaColors
import com.gastos.quincenal.utils.DateUtils
import java.time.LocalDate

/** Lista de ingresos del mes, agrupados por quincena. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeListScreen(
    onAddIncome: () -> Unit,
    onEditIncome: (Long, LocalDate) -> Unit,
    viewModel: IncomeListViewModel = viewModel(factory = ViewModelFactories.incomeList)
) {
    val yearMonth by viewModel.yearMonth.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = quincenaColors

    var pendingDelete by remember { mutableStateOf<IncomeOccurrence?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ingresos") },
                actions = {
                    IconButton(onClick = onAddIncome) {
                        Icon(Icons.Filled.Add, contentDescription = "Nuevo ingreso")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val data = overview

        if (data == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val formatter = data.formatter

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                MonthNavigator(
                    yearMonth = yearMonth,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    onToday = viewModel::showToday
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Total de ingresos de ${DateUtils.monthTitleCapitalized(yearMonth)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatter.format(data.summary.income),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.positive,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Quincena.entries.forEach { quincena ->
                val quincenaSummary = data.summary.summaryFor(quincena)
                val quincenaIncomes = data.incomes.filter { it.quincena == quincena }

                item(key = "header-${quincena.name}") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.container(quincena)
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(
                                text = quincena.displayName.uppercase(DateUtils.LOCALE),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.accent(quincena),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = quincenaSummary.dayRangeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onContainer(quincena)
                            )
                        }
                    }
                }

                if (quincenaIncomes.isEmpty()) {
                    item(key = "empty-${quincena.name}") {
                        EmptyState(
                            title = "Sin ingresos",
                            message = "Agrega el sueldo o cualquier otro ingreso de esta quincena."
                        )
                    }
                } else {
                    items(quincenaIncomes, key = { it.occurrenceKey }) { income ->
                        IncomeRow(
                            occurrence = income,
                            formatter = formatter,
                            onClick = { onEditIncome(income.incomeId, income.date) },
                            onLongClick = { pendingDelete = income }
                        )
                    }

                    item(key = "total-${quincena.name}") {
                        LabeledAmountRow(
                            label = "TOTAL",
                            amount = formatter.format(quincenaSummary.income),
                            amountColor = colors.positive,
                            emphasize = true
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { income ->
        ConfirmDialog(
            title = "Eliminar ingreso",
            message = "Se eliminara \"${income.name}\"" +
                if (income.isRecurring) " en todos los meses." else ".",
            confirmText = "Eliminar",
            onConfirm = {
                viewModel.deleteSeries(income.incomeId)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
