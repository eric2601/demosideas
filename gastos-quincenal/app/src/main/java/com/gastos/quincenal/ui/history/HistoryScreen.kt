package com.gastos.quincenal.ui.history

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.ui.components.EmptyState
import com.gastos.quincenal.ui.components.LabeledAmountRow
import com.gastos.quincenal.ui.theme.quincenaColors
import com.gastos.quincenal.utils.DateUtils

/** Historial anual: totales de cada mes del ano seleccionado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = ViewModelFactories.history)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formatter by viewModel.formatter.collectAsStateWithLifecycle()
    val colors = quincenaColors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = viewModel::previousYear) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Ano anterior"
                        )
                    }
                    Text(
                        text = state.year.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = viewModel::nextYear) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Ano siguiente"
                        )
                    }
                }
            }

            if (state.isLoading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                    }
                }
                return@LazyColumn
            }

            val active = state.entries.filter {
                it.income != Money.ZERO || it.expenses != Money.ZERO
            }

            if (active.isEmpty()) {
                item {
                    EmptyState(
                        title = "Sin movimientos en ${state.year}",
                        message = "Registra gastos o ingresos para construir el historial."
                    )
                }
            } else {
                item {
                    val yearIncome = Money(active.sumOf { it.income.cents })
                    val yearExpenses = Money(active.sumOf { it.expenses.cents })
                    val yearRemaining = yearIncome - yearExpenses

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Total del ano ${state.year}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            LabeledAmountRow("Ingresos", formatter.format(yearIncome))
                            LabeledAmountRow("Gastos", formatter.format(yearExpenses))
                            LabeledAmountRow(
                                label = "Sobrante",
                                amount = formatter.format(yearRemaining),
                                amountColor = colors.forBalance(yearRemaining.isNegative),
                                emphasize = true
                            )
                        }
                    }
                }

                items(active, key = { it.yearMonth.toString() }) { entry ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = DateUtils.monthName(entry.yearMonth).uppercase(DateUtils.LOCALE),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            LabeledAmountRow("Ingresos", formatter.format(entry.income))
                            LabeledAmountRow("Gastos", formatter.format(entry.expenses))
                            LabeledAmountRow(
                                label = "Sobrante",
                                amount = formatter.format(entry.remaining),
                                amountColor = colors.forBalance(entry.remaining.isNegative),
                                emphasize = true
                            )
                        }
                    }
                }
            }
        }
    }
}
