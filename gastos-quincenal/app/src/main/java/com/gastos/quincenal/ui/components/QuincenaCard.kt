package com.gastos.quincenal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gastos.quincenal.domain.model.QuincenaSummary
import com.gastos.quincenal.ui.theme.quincenaColors
import com.gastos.quincenal.utils.CurrencyFormatter
import com.gastos.quincenal.utils.DateUtils

/**
 * Tarjeta de una quincena: azul cielo para la primera, naranja para la segunda.
 * Muestra ingresos, gastos y el sobrante calculado.
 */
@Composable
fun QuincenaCard(
    summary: QuincenaSummary,
    formatter: CurrencyFormatter,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val colors = quincenaColors
    val container = colors.container(summary.quincena)
    val onContainer = colors.onContainer(summary.quincena)
    val accent = colors.accent(summary.quincena)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(containerColor = container),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = summary.quincena.displayName.uppercase(DateUtils.LOCALE),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = summary.dayRangeLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = onContainer.copy(alpha = 0.8f)
                )
            }

            BudgetUsageBar(ratio = summary.usageRatio, color = accent)

            LabeledAmountRow(
                label = "Ingresos",
                amount = formatter.format(summary.income),
                amountColor = onContainer
            )
            LabeledAmountRow(
                label = "Gastos",
                amount = formatter.format(summary.expenses),
                amountColor = onContainer
            )
            LabeledAmountRow(
                label = "SOBRA",
                amount = formatter.format(summary.remaining),
                amountColor = colors.forBalance(summary.isOverBudget),
                emphasize = true
            )

            if (summary.pendingExpenses.isPositive) {
                Text(
                    text = "Pendiente de pagar: ${formatter.format(summary.pendingExpenses)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}
