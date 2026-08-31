package com.gastos.quincenal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gastos.quincenal.domain.calc.WeekCalculator
import com.gastos.quincenal.domain.model.DayOfWeekNames
import com.gastos.quincenal.domain.model.DaySummary
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.ui.theme.quincenaColors
import com.gastos.quincenal.utils.CurrencyFormatter
import com.gastos.quincenal.utils.DateUtils
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calendario mensual de domingo a sabado.
 *
 * Los dias 1 a 15 se pintan en azul cielo y del 16 al ultimo dia del mes en
 * naranja, con una separacion visible entre ambas quincenas. Cada dia con
 * movimientos muestra un indicador debajo del numero.
 */
@Composable
fun MonthCalendar(
    yearMonth: YearMonth,
    byDay: Map<LocalDate, DaySummary>,
    selectedDate: LocalDate?,
    formatter: CurrencyFormatter,
    onDaySelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxWidth()) {
        WeekDayHeader()

        val cells = calendarCells(yearMonth)
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            // Separacion entre quincenas cuando la fila cruza del dia 15 al 16.
            val crossesQuincena = week.any { it?.dayOfMonth == Quincena.SECOND_START_DAY } &&
                week.any { it != null && it.dayOfMonth <= Quincena.FIRST_END_DAY }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (date == null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.85f)
                            )
                        } else {
                            DayCell(
                                date = date,
                                summary = byDay[date],
                                isSelected = date == selectedDate,
                                isToday = date == today,
                                formatter = formatter,
                                onClick = { onDaySelected(date) }
                            )
                        }
                    }
                }
            }

            if (crossesQuincena) {
                QuincenaSeparator()
            }
        }

        CalendarLegend()
    }
}

@Composable
private fun WeekDayHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DayOfWeekNames.ORDER.forEach { day ->
            Text(
                text = DayOfWeekNames.short(day),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuincenaSeparator() {
    val colors = quincenaColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.firstAccent)
        )
        Text(
            text = "2a QUINCENA",
            style = MaterialTheme.typography.labelSmall,
            color = colors.secondAccent,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.secondAccent)
        )
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    isSelected: Boolean,
    isToday: Boolean,
    formatter: CurrencyFormatter,
    onClick: () -> Unit
) {
    val colors = quincenaColors
    val quincena = Quincena.of(date)
    val container = colors.container(quincena)
    val onContainer = colors.onContainer(quincena)
    val accent = colors.accent(quincena)

    val expensesCount = summary?.expenses?.size ?: 0
    val hasIncome = summary?.incomes?.isNotEmpty() == true
    val total = summary?.totalExpenses

    val description = buildString {
        append(DateUtils.formatLong(date))
        append(", ")
        append(quincena.displayName)
        if (expensesCount > 0 && total != null) {
            append(", $expensesCount gastos por ${formatter.format(total)}")
        } else {
            append(", sin gastos")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .then(
                if (isSelected) {
                    Modifier.border(BorderStroke(2.dp, accent), RoundedCornerShape(10.dp))
                } else if (isToday) {
                    Modifier.border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
                        RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = onContainer,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium
            )

            if (expensesCount > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(minOf(expensesCount, MAX_DOTS)) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                }
                if (total != null) {
                    Text(
                        text = formatter.formatCompact(total),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
            }

            if (hasIncome) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.positive)
                )
            }
        }
    }
}

@Composable
private fun CalendarLegend() {
    val colors = quincenaColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegendItem(color = colors.firstAccent, label = "1a quincena (1-15)")
        LegendItem(color = colors.secondAccent, label = "2a quincena (16-fin)")
        LegendItem(color = colors.positive, label = "Ingreso")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Celdas de la rejilla: los huecos iniciales anteriores al dia 1 se representan
 * con `null` para que el dia 1 caiga bajo el nombre de su dia de la semana.
 */
private fun calendarCells(yearMonth: YearMonth): List<LocalDate?> {
    val firstDay = yearMonth.atDay(1)
    val leadingBlanks = WeekCalculator.sundayBasedIndex(firstDay.dayOfWeek)
    val days = (1..yearMonth.lengthOfMonth()).map { yearMonth.atDay(it) }
    val cells = List(leadingBlanks) { null } + days
    val remainder = cells.size % DAYS_PER_WEEK
    val trailingBlanks = if (remainder == 0) 0 else DAYS_PER_WEEK - remainder
    return cells + List(trailingBlanks) { null }
}

private const val DAYS_PER_WEEK = 7
private const val MAX_DOTS = 3
