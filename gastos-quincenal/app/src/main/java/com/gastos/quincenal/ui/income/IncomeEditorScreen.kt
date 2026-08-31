package com.gastos.quincenal.ui.income

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gastos.quincenal.di.ViewModelFactories
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.RecurrenceEnd
import com.gastos.quincenal.ui.components.ChipRow
import com.gastos.quincenal.ui.components.DateField
import com.gastos.quincenal.ui.components.RadioGroup
import com.gastos.quincenal.ui.components.SectionHeader
import com.gastos.quincenal.ui.components.VerticalSpace
import com.gastos.quincenal.ui.theme.quincenaColors
import java.time.LocalDate

/** Formulario de alta y edicion de un ingreso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomeEditorScreen(
    incomeId: Long,
    initialDate: LocalDate?,
    onDone: () -> Unit,
    viewModel: IncomeEditorViewModel = viewModel(factory = ViewModelFactories.incomeEditor)
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = quincenaColors

    LaunchedEffect(incomeId, initialDate) {
        viewModel.start(incomeId, initialDate)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Editar ingreso" else "Nuevo ingreso") },
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
                label = { Text("Concepto") },
                placeholder = { Text("Sueldo") },
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

            SectionHeader(title = "Frecuencia")
            RadioGroup(
                options = RecurrenceEnd.entries,
                selected = state.recurrenceEnd,
                label = {
                    when (it) {
                        RecurrenceEnd.ONLY_THIS_MONTH -> "Solo este mes"
                        RecurrenceEnd.FOREVER -> "Repetir cada mes"
                        RecurrenceEnd.UNTIL_DATE -> "Repetir hasta una fecha"
                    }
                },
                onSelect = viewModel::setRecurrenceEnd
            )

            if (state.recurrenceEnd != RecurrenceEnd.ONLY_THIS_MONTH) {
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
                    text = if (state.isReceived) "Recibido" else "Por recibir",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(checked = state.isReceived, onCheckedChange = viewModel::setReceived)
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
                OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                    Text("CANCELAR")
                }
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
