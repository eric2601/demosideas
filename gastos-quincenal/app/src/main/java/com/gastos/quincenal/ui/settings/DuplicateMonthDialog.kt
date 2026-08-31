package com.gastos.quincenal.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gastos.quincenal.domain.usecase.DuplicateOptions
import com.gastos.quincenal.utils.DateUtils
import java.time.YearMonth

/**
 * Pregunta que se desea copiar al duplicar un mes.
 *
 * Los gastos recurrentes que la regla ya genera en el mes destino no se vuelven
 * a copiar; el resultado indica cuantos se omitieron por esa razon.
 */
@Composable
fun DuplicateMonthDialog(
    source: YearMonth,
    target: YearMonth,
    onConfirm: (DuplicateOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var recurring by remember { mutableStateOf(true) }
    var oneTime by remember { mutableStateOf(true) }
    var incomes by remember { mutableStateOf(true) }
    var paid by remember { mutableStateOf(false) }
    var keepCategories by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicar mes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "De ${DateUtils.monthTitleCapitalized(source)} " +
                        "a ${DateUtils.monthTitleCapitalized(target)}.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Que deseas copiar?",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
                CheckRow("Gastos recurrentes", recurring) { recurring = it }
                CheckRow("Gastos unicos", oneTime) { oneTime = it }
                CheckRow("Ingresos", incomes) { incomes = it }
                CheckRow("Gastos ya pagados", paid) { paid = it }
                CheckRow("Conservar categorias", keepCategories) { keepCategories = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        DuplicateOptions(
                            recurringExpenses = recurring,
                            oneTimeExpenses = oneTime,
                            incomes = incomes,
                            paidExpenses = paid,
                            keepCategories = keepCategories
                        )
                    )
                }
            ) { Text("Duplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}
