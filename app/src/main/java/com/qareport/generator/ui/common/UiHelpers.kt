package com.qareport.generator.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Muestra en el Snackbar los mensajes emitidos por el ViewModel y los consume,
 * de modo que no se repiten al rotar la pantalla.
 */
@Composable
fun ObserveMessages(viewModel: BaseViewModel, snackbarHostState: SnackbarHostState) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(message?.id) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = current.text,
            duration = if (current.isError) SnackbarDuration.Long else SnackbarDuration.Short
        )
        viewModel.consumeMessage()
    }
}

/** Campo de texto estándar del formulario, con soporte de error bajo el campo. */
@Composable
fun QaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    required: Boolean = false,
    placeholder: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(if (required) "$label *" else label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(capitalization = capitalization),
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
    )
}

/** Selector de una opción entre varias, representado con chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipSelector(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors()
            )
        }
    }
}
