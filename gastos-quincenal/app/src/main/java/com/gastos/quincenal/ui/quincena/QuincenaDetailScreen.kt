package com.gastos.quincenal.ui.quincena

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.ui.components.EmptyState
import com.gastos.quincenal.ui.components.ExpenseRow
import com.gastos.quincenal.ui.components.IncomeRow
import com.gastos.quincenal.ui.components.LabeledAmountRow
import com.gastos.quincenal.ui.components.QuincenaCard
import com.gastos.quincenal.ui.components.SectionHeader
import com.gastos.quincenal.ui.components.ThinDivider
import com.gastos.quincenal.ui.theme.quincenaColors
import com.gastos.quincenal.utils.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/**
 * Detalle de una quincena: ingresos, gastos, total y sobrante, con accesos para
 * agregar movimientos directamente en ese periodo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuincenaDetailScreen(
    yearMonth: YearMonth,
    quincena: Quincena,
    onBack: () -> Unit,
    onAddExpense: (LocalDate) -> Unit,
    onAddIncome: (LocalDate) -> Unit,
    onEditExpense: (Long, LocalDate) -> Unit,
    viewModel: QuincenaViewModel = viewModel(factory = ViewModelFactories.quincena)
) {
    LaunchedEffect(yearMonth, quincena) {
        viewModel.showMonth(yearMonth)
        viewModel.selectQuincena(quincena)
    }

    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val selectedQuincena by viewModel.quincena.collectAsStateWithLifecycle()
    val colors = quincenaColors

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${selectedQuincena.displayName} - ${DateUtils.monthTitleCapitalized(yearMonth)}")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
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
        val summary = data.summary.summaryFor(selectedQuincena)
        val expenses = data.expenses.filter { it.quincena == selectedQuincena }
        val incomes = data.incomes.filter { it.quincena == selectedQuincena }
        val defaultDate = summary.startDate

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                QuincenaCard(summary = summary, formatter = formatter)
            }

            item { SectionHeader(title = "Ingresos", trailing = formatter.format(summary.income)) }

            if (incomes.isEmpty()) {
                item {
                    EmptyState(
                        title = "Sin ingresos",
                        message = "Aun no registras ingresos en esta quincena."
                    )
                }
            } else {
                items(incomes, key = { it.occurrenceKey }) { income ->
                    IncomeRow(occurrence = income, formatter = formatter)
                }
            }

            item { SectionHeader(title = "Gastos", trailing = "${expenses.size} movimientos") }

            if (expenses.isEmpty()) {
                item {
                    EmptyState(
                        title = "Sin gastos",
                        message = "Aun no registras gastos en esta quincena."
                    )
                }
            } else {
                items(expenses, key = { it.occurrenceKey }) { occurrence ->
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
                Column(modifier = Modifier.fillMaxWidth()) {
                    ThinDivider()
                    LabeledAmountRow(
                        label = "TOTAL",
                        amount = formatter.format(summary.expenses),
                        emphasize = true
                    )
                    LabeledAmountRow(
                        label = "SOBRANTE",
                        amount = formatter.format(summary.remaining),
                        amountColor = colors.forBalance(summary.isOverBudget),
                        emphasize = true
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAddExpense(defaultDate) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(" GASTO")
                    }
                    OutlinedButton(
                        onClick = { onAddIncome(defaultDate) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(" INGRESO")
                    }
                }
            }
        }
    }
}
