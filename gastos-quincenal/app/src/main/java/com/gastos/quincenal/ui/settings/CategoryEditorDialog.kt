package com.gastos.quincenal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gastos.quincenal.domain.model.Category

/** Paleta ofrecida al crear o editar una categoria. */
private val PALETTE = listOf(
    0xFF2E7D32, 0xFFEF6C00, 0xFFD84315, 0xFF5E35B1, 0xFF0277BD,
    0xFF00838F, 0xFFC62828, 0xFF6D4C41, 0xFFAD1457, 0xFF37474F,
    0xFF00897B, 0xFF757575
).map { it.toInt() }

/** Alta y edicion de una categoria personalizada. */
@Composable
fun CategoryEditorDialog(
    category: Category?,
    onConfirm: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category?.name.orEmpty()) }
    var colorArgb by remember { mutableStateOf(category?.colorArgb ?: PALETTE.first()) }
    var forIncome by remember { mutableStateOf(category?.forIncome ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Nueva categoria" else "Editar categoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.take(6).forEach { option ->
                        ColorSwatch(option, option == colorArgb) { colorArgb = option }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE.drop(6).forEach { option ->
                        ColorSwatch(option, option == colorArgb) { colorArgb = option }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { forIncome = !forIncome },
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = forIncome,
                        onCheckedChange = { forIncome = it }
                    )
                    Text(
                        text = "Es una categoria de ingresos",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        Category(
                            id = category?.id ?: 0L,
                            name = name.trim(),
                            colorArgb = colorArgb,
                            forIncome = forIncome,
                            isDefault = category?.isDefault ?: false
                        )
                    )
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ColorSwatch(colorArgb: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(colorArgb))
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
    ) {}
}
