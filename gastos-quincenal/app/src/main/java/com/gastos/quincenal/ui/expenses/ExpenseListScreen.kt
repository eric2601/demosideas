package com.gastos.quincenal.ui.expenses

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.gastos.quincenal.domain.model.ExpenseOccurrence
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.domain.model.QuincenaSummary
import com.gastos.quincenal.ui.components.EmptyState
import com.gastos.quincenal.ui.components.ExpenseRow
import com.gastos.quincenal.ui.components.IncomeRow
import com.gastos.quincenal.ui.components.LabeledAmountRow
import com.gastos.quincenal.ui.components.MonthNavigator
import com.gastos.quincenal.ui.components.ThinDivider
import com.gastos.quincenal.ui.theme.quincenaColors
import com.gastos.quincenal.utils.CurrencyFormatter
import com.gastos.quincenal.utils.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/**
 * Lista completa de gastos del mes agrupada por quincena, replicando la lectura
 * de la hoja de referencia: ingresos, gastos, total y sobrante de cada bloque.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    onAddExpense: () -> Unit,
    onEditExpense: (Long, LocalDate) -> Unit,
    onOpenQuincena: (YearMonth, Quincena) -> Unit,
    viewModel: ExpenseListViewModel = viewModel(factory = ViewModelFactories.expenseList)
) {
    val yearMonth by viewModel.yearMonth.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var actionTarget by remember { mutableStateOf<ExpenseOccurrence?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it.text)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gastos") },
                actions = {
                    IconButton(onClick = onAddExpense) {
                        Icon(Icons.Filled.Add, contentDescription = "Nuevo gasto")
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
        val visible = data.expenses.filter { occurrence ->
            val matchesFilter = when (filter) {
                ExpenseFilter.ALL -> true
                ExpenseFilter.PENDING -> !occurrence.isPaid
                ExpenseFilter.PAID -> occurrence.isPaid
                ExpenseFilter.RECURRING -> occurrence.isRecurring
            }
            val matchesCategory = categoryFilter == null || occurrence.categoryId == categoryFilter
            matchesFilter && matchesCategory
        }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseFilter.entries.forEach { option ->
                        FilterChip(
                            selected = filter == option,
                            onClick = { viewModel.setFilter(option) },
                            label = { Text(option.displayName) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    data.categories.filterNot { it.forIncome }.forEach { category ->
                        FilterChip(
                            selected = categoryFilter == category.id,
                            onClick = { viewModel.setCategoryFilter(category.id) },
                            label = { Text(category.name) }
                        )
                    }
                }
            }

            Quincena.entries.forEach { quincena ->
                val quincenaSummary = data.summary.summaryFor(quincena)
                val quincenaExpenses = visible.filter { it.quincena == quincena }
                val quincenaIncomes = data.incomes.filter { it.quincena == quincena }

                item(key = "header-${quincena.name}") {
                    QuincenaBlockHeader(
                        summary = quincenaSummary,
                        yearMonth = yearMonth,
                        onClick = { onOpenQuincena(yearMonth, quincena) }
                    )
                }

                items(quincenaIncomes, key = { "income-${it.occurrenceKey}" }) { income ->
                    IncomeRow(occurrence = income, formatter = formatter)
                }

                if (quincenaExpenses.isEmpty()) {
                    item(key = "empty-${quincena.name}") {
                        EmptyState(
                            title = "Sin gastos",
                            message = "No hay gastos que coincidan con el filtro en esta quincena."
                        )
                    }
                } else {
                    items(quincenaExpenses, key = { "expense-${it.occurrenceKey}" }) { occurrence ->
                        ExpenseRow(
                            occurrence = occurrence,
                            formatter = formatter,
                            onClick = { onEditExpense(occurrence.expenseId, occurrence.date) },
                            onLongClick = { actionTarget = occurrence }
                        )
                    }
                }

                item(key = "totals-${quincena.name}") {
                    QuincenaBlockTotals(summary = quincenaSummary, formatter = formatter)
                }
            }
        }
    }

    actionTarget?.let { occurrence ->
        ExpenseActionsSheet(
            occurrence = occurrence,
            onDismiss = { actionTarget = null },
            onTogglePaid = {
                viewModel.togglePaid(occurrence.expenseId, occurrence.date, !occurrence.isPaid)
                actionTarget = null
            },
            onEdit = {
                onEditExpense(occurrence.expenseId, occurrence.date)
                actionTarget = null
            },
            onDuplicate = {
                viewModel.duplicate(occurrence.expenseId)
                actionTarget = null
            },
            onDeleteOccurrence = {
                viewModel.deleteOccurrence(occurrence.expenseId, occurrence.date)
                actionTarget = null
            },
            onDeleteSeries = {
                viewModel.deleteSeries(occurrence.expenseId)
                actionTarget = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuincenaBlockHeader(
    summary: QuincenaSummary,
    yearMonth: YearMonth,
    onClick: () -> Unit
) {
    val colors = quincenaColors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container(summary.quincena)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = summary.quincena.displayName.uppercase(DateUtils.LOCALE),
                style = MaterialTheme.typography.titleMedium,
                color = colors.accent(summary.quincena),
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${summary.dayRangeLabel} ${DateUtils.monthName(yearMonth).uppercase(DateUtils.LOCALE)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onContainer(summary.quincena)
            )
        }
    }
}

@Composable
private fun QuincenaBlockTotals(summary: QuincenaSummary, formatter: CurrencyFormatter) {
    val colors = quincenaColors
    Column(modifier = Modifier.fillMaxWidth()) {
        ThinDivider()
        LabeledAmountRow(
            label = "TOTAL GASTOS",
            amount = formatter.format(summary.expenses),
            emphasize = true
        )
        LabeledAmountRow(
            label = "SOBRA",
            amount = formatter.format(summary.remaining),
            amountColor = colors.forBalance(summary.isOverBudget),
            emphasize = true
        )
    }
}
