package com.gastos.quincenal.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.ui.components.BudgetAlertCard
import com.gastos.quincenal.ui.components.EmptyState
import com.gastos.quincenal.ui.components.ExpenseRow
import com.gastos.quincenal.ui.components.LabeledAmountRow
import com.gastos.quincenal.ui.components.MonthNavigator
import com.gastos.quincenal.ui.components.QuincenaCard
import com.gastos.quincenal.ui.components.SectionHeader
import com.gastos.quincenal.ui.components.ThinDivider
import com.gastos.quincenal.ui.components.VerticalSpace
import com.gastos.quincenal.ui.theme.quincenaColors
import com.gastos.quincenal.utils.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tablero mensual: ingresos del mes, las dos tarjetas de quincena, avisos,
 * resumen semanal y los proximos gastos pendientes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenQuincena: (YearMonth, Quincena) -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenHistory: () -> Unit,
    onEditExpense: (Long, LocalDate) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = ViewModelFactories.dashboard)
) {
    val yearMonth by viewModel.yearMonth.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val colors = quincenaColors

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control de Gastos") },
                actions = {
                    IconButton(onClick = onOpenStatistics) {
                        Icon(Icons.Filled.BarChart, contentDescription = "Estadisticas")
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "Historial")
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
        val summary = data.summary
        val pending = data.expenses.filterNot { it.isPaid }.take(MAX_PENDING_PREVIEW)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ingresos del mes",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatter.format(summary.income),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            items(data.alerts, key = { it.title + it.message }) { alert ->
                BudgetAlertCard(alert = alert, formatter = formatter)
            }

            item {
                QuincenaCard(
                    summary = summary.first,
                    formatter = formatter,
                    onClick = { onOpenQuincena(yearMonth, Quincena.PRIMERA) }
                )
            }

            item {
                QuincenaCard(
                    summary = summary.second,
                    formatter = formatter,
                    onClick = { onOpenQuincena(yearMonth, Quincena.SEGUNDA) }
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "TOTAL DEL MES",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        VerticalSpace(8)
                        LabeledAmountRow("Ingresos", formatter.format(summary.income))
                        LabeledAmountRow("Gastos", formatter.format(summary.expenses))
                        ThinDivider()
                        LabeledAmountRow(
                            label = "SOBRA",
                            amount = formatter.format(summary.remaining),
                            amountColor = colors.forBalance(summary.isOverBudget),
                            emphasize = true
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Resumen semanal",
                    trailing = DateUtils.monthTitleCapitalized(yearMonth)
                )
            }

            items(data.weeks, key = { it.index }) { week ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = week.label,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = week.rangeLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LabeledAmountRow("Ingresos", formatter.format(week.income))
                        LabeledAmountRow("Gastos", formatter.format(week.expenses))
                        LabeledAmountRow(
                            label = "Sobra",
                            amount = formatter.format(week.remaining),
                            amountColor = colors.forBalance(week.remaining.isNegative)
                        )
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Gastos pendientes",
                    trailing = formatter.format(summary.pendingExpenses)
                )
            }

            if (pending.isEmpty()) {
                item {
                    EmptyState(
                        title = "Todo al corriente",
                        message = "No hay gastos pendientes en ${DateUtils.monthTitleCapitalized(yearMonth)}."
                    )
                }
            } else {
                items(pending, key = { it.occurrenceKey }) { occurrence ->
                    ExpenseRow(
                        occurrence = occurrence,
                        formatter = formatter,
                        onClick = { onEditExpense(occurrence.expenseId, occurrence.date) },
                        onLongClick = {
                            viewModel.togglePaid(
                                occurrence.expenseId,
                                occurrence.date,
                                !occurrence.isPaid
                            )
                        }
                    )
                }
            }

            item {
                OutlinedButton(
                    onClick = viewModel::copyToNextMonth,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text(
                        text = "  COPIAR GASTOS AL SIGUIENTE MES",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

private const val MAX_PENDING_PREVIEW = 8
