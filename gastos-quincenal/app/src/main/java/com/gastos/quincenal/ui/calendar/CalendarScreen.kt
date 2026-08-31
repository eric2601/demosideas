package com.gastos.quincenal.ui.calendar

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.ui.components.EmptyState
import com.gastos.quincenal.ui.components.ExpenseRow
import com.gastos.quincenal.ui.components.IncomeRow
import com.gastos.quincenal.ui.components.LabeledAmountRow
import com.gastos.quincenal.ui.components.MonthCalendar
import com.gastos.quincenal.ui.components.MonthNavigator
import com.gastos.quincenal.ui.components.SectionHeader
import com.gastos.quincenal.ui.components.ThinDivider
import com.gastos.quincenal.ui.theme.quincenaColors
import com.gastos.quincenal.utils.DateUtils
import java.time.LocalDate

/**
 * Calendario mensual interactivo. Al tocar un dia se muestran sus movimientos
 * con el total del dia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onAddExpense: (LocalDate) -> Unit,
    onEditExpense: (Long, LocalDate) -> Unit,
    viewModel: CalendarViewModel = viewModel(factory = ViewModelFactories.calendar)
) {
    val yearMonth by viewModel.yearMonth.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val colors = quincenaColors

    Scaffold(
        topBar = { TopAppBar(title = { Text("Calendario") }) }
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
        val daySummary = selectedDate?.let { data.byDay[it] }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                MonthNavigator(
                    yearMonth = yearMonth,
                    onPrevious = {
                        viewModel.showPreviousMonth()
                        viewModel.onMonthChanged()
                    },
                    onNext = {
                        viewModel.showNextMonth()
                        viewModel.onMonthChanged()
                    },
                    onToday = {
                        viewModel.showToday()
                        viewModel.selectDate(LocalDate.now())
                    }
                )
            }

            item {
                MonthCalendar(
                    yearMonth = yearMonth,
                    byDay = data.byDay,
                    selectedDate = selectedDate,
                    formatter = formatter,
                    onDaySelected = viewModel::selectDate
                )
            }

            if (daySummary == null) {
                item {
                    EmptyState(
                        title = "Elige un dia",
                        message = "Toca cualquier dia del calendario para ver sus gastos e ingresos."
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colors.container(daySummary.quincena)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = DateUtils.formatLong(daySummary.date),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.onContainer(daySummary.quincena),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = daySummary.quincena.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent(daySummary.quincena)
                            )
                            ThinDivider()
                            LabeledAmountRow(
                                label = "Total de gastos del dia",
                                amount = formatter.format(daySummary.totalExpenses),
                                amountColor = colors.onContainer(daySummary.quincena),
                                emphasize = true
                            )
                            if (daySummary.totalIncome.isPositive) {
                                LabeledAmountRow(
                                    label = "Ingresos del dia",
                                    amount = formatter.format(daySummary.totalIncome),
                                    amountColor = colors.positive
                                )
                            }
                        }
                    }
                }

                if (daySummary.incomes.isNotEmpty()) {
                    item { SectionHeader(title = "Ingresos del dia") }
                    items(daySummary.incomes, key = { it.occurrenceKey }) { income ->
                        IncomeRow(occurrence = income, formatter = formatter, showDate = false)
                    }
                }

                item {
                    SectionHeader(
                        title = "Gastos del dia",
                        trailing = "${daySummary.expenses.size} movimientos"
                    )
                }

                if (daySummary.expenses.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Sin gastos",
                            message = "No hay gastos registrados este dia."
                        )
                    }
                } else {
                    items(daySummary.expenses, key = { it.occurrenceKey }) { occurrence ->
                        ExpenseRow(
                            occurrence = occurrence,
                            formatter = formatter,
                            showDate = false,
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
                    TextButton(
                        onClick = { onAddExpense(daySummary.date) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("  AGREGAR GASTO EN ESTE DIA")
                    }
                }
            }
        }
    }
}
