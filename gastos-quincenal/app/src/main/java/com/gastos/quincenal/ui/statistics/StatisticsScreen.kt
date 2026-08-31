package com.gastos.quincenal.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.ui.components.BarDatum
import com.gastos.quincenal.ui.components.HorizontalBarChart
import com.gastos.quincenal.ui.components.MonthNavigator
import com.gastos.quincenal.ui.components.SectionHeader
import com.gastos.quincenal.ui.components.VerticalBarChart
import com.gastos.quincenal.ui.theme.quincenaColors

/**
 * Estadisticas del mes: ingresos contra gastos, reparto por quincena, por semana
 * y por categoria.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = viewModel(factory = ViewModelFactories.statistics)
) {
    val yearMonth by viewModel.yearMonth.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val colors = quincenaColors

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadisticas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = "Historial")
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
        val summary = data.summary

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                ChartCard(title = "Ingresos vs gastos") {
                    HorizontalBarChart(
                        data = listOf(
                            BarDatum("Ingresos", summary.income, colors.positive),
                            BarDatum("Gastos", summary.expenses, colors.secondAccent),
                            BarDatum(
                                label = "Sobrante",
                                value = summary.remaining,
                                color = colors.forBalance(summary.isOverBudget)
                            )
                        ),
                        formatter = formatter
                    )
                }
            }

            item {
                ChartCard(title = "Gastos por quincena") {
                    HorizontalBarChart(
                        data = listOf(
                            BarDatum(
                                label = Quincena.PRIMERA.shortName,
                                value = summary.first.expenses,
                                color = colors.firstAccent,
                                caption = "Sobra ${formatter.format(summary.first.remaining)}"
                            ),
                            BarDatum(
                                label = Quincena.SEGUNDA.shortName,
                                value = summary.second.expenses,
                                color = colors.secondAccent,
                                caption = "Sobra ${formatter.format(summary.second.remaining)}"
                            )
                        ),
                        formatter = formatter
                    )
                }
            }

            item {
                ChartCard(title = "Gastos por semana") {
                    VerticalBarChart(
                        data = data.weeks.map { week ->
                            BarDatum(
                                label = "S${week.index}",
                                value = week.expenses,
                                color = colors.secondAccent
                            )
                        },
                        formatter = formatter
                    )
                }
            }

            item {
                ChartCard(title = "Evolucion del sobrante por semana") {
                    VerticalBarChart(
                        data = data.weeks.map { week ->
                            BarDatum(
                                label = "S${week.index}",
                                value = week.remaining,
                                color = colors.forBalance(week.remaining.isNegative)
                            )
                        },
                        formatter = formatter
                    )
                }
            }

            item {
                ChartCard(title = "Gastos por categoria") {
                    HorizontalBarChart(
                        data = data.byCategory.map { total ->
                            BarDatum(
                                label = total.categoryName,
                                value = total.total,
                                color = Color(total.colorArgb),
                                caption = "${total.count} movimientos - " +
                                    "${(total.share(summary.expenses) * 100).toInt()}% del mes"
                            )
                        },
                        formatter = formatter
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(title = title)
            content()
        }
    }
}
