package com.gastos.quincenal.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.domain.model.DayOfWeekNames
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.RecurrenceEnd
import com.gastos.quincenal.ui.components.ChipRow
import com.gastos.quincenal.ui.components.DateField
import com.gastos.quincenal.ui.components.RadioGroup
import com.gastos.quincenal.ui.components.SectionHeader
import com.gastos.quincenal.ui.components.VerticalSpace
import com.gastos.quincenal.ui.theme.quincenaColors
import java.time.LocalDate

/**
 * Formulario de alta y edicion de un gasto.
 *
 * La quincena no se pide: se calcula a partir de la fecha y solo se muestra
 * como informacion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditorScreen(
    expenseId: Long,
    initialDate: LocalDate?,
    onDone: () -> Unit,
    viewModel: ExpenseEditorViewModel = viewModel(factory = ViewModelFactories.expenseEditor)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = quincenaColors

    LaunchedEffect(expenseId, initialDate) {
        viewModel.start(expenseId, initialDate)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Editar gasto" else "Nuevo gasto") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            VerticalSpace(4)

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Nombre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::setAmount,
                label = { Text("Cantidad") },
                prefix = { Text(state.formatter.symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            SectionHeader(title = "Categoria")
            ChipRow(
                options = state.categories,
                selected = state.categories.firstOrNull { it.id == state.categoryId },
                label = { it.name },
                onSelect = { viewModel.setCategory(it.id) }
            )

            SectionHeader(title = "Tipo de gasto")
            ChipRow(
                options = ExpenseType.entries,
                selected = state.type,
                label = { it.displayName },
                onSelect = viewModel::setType
            )

            DateField(
                label = "Fecha",
                date = state.date,
                onDateSelected = viewModel::setDate
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colors.container(state.quincena)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Quincena asignada automaticamente",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onContainer(state.quincena)
                    )
                    Text(
                        text = state.quincena.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.accent(state.quincena),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            SectionHeader(title = "Este gasto se repite?")
            RadioGroup(
                options = RecurrenceEnd.entries,
                selected = state.recurrenceEnd,
                label = {
                    when (it) {
                        RecurrenceEnd.ONLY_THIS_MONTH -> "NO, solo este mes"
                        RecurrenceEnd.FOREVER -> "SI, repetir indefinidamente"
                        RecurrenceEnd.UNTIL_DATE -> "SI, repetir hasta una fecha"
                    }
                },
                onSelect = viewModel::setRecurrenceEnd
            )

            if (state.recurrenceEnd != RecurrenceEnd.ONLY_THIS_MONTH) {
                SectionHeader(title = "Frecuencia")
                ChipRow(
                    options = listOf(
                        Frequency.WEEKLY,
                        Frequency.BIWEEKLY,
                        Frequency.MONTHLY,
                        Frequency.YEARLY
                    ),
                    selected = state.frequency,
                    label = { it.displayName },
                    onSelect = viewModel::setFrequency
                )
            }

            if (state.showDayOfWeekPicker) {
                SectionHeader(title = "Dia de la semana")
                ChipRow(
                    options = DayOfWeekNames.ORDER,
                    selected = state.dayOfWeek,
                    label = { DayOfWeekNames.full(it) },
                    onSelect = viewModel::setDayOfWeek
                )
            }

            if (state.showEndDatePicker) {
                DateField(
                    label = "Repetir hasta",
                    date = state.endDate,
                    onDateSelected = { viewModel.setEndDate(it) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (state.isPaid) "Pagado" else "Pendiente",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(checked = state.isPaid, onCheckedChange = viewModel::setPaid)
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Notas") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            state.errors.forEach { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f)
                ) { Text("CANCELAR") }

                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.weight(1f)
                ) { Text("GUARDAR") }
            }

            VerticalSpace(32)
        }
    }
}
