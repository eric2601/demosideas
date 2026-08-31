package com.gastos.quincenal.domain.model

/**
 * Categoria de gasto o de ingreso. El usuario puede crear las suyas.
 *
 * @param colorArgb color en formato ARGB para los indicadores y las graficas.
 * @param isDefault marca las categorias sembradas al instalar; no impide editarlas.
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val colorArgb: Int,
    val forIncome: Boolean = false,
    val isDefault: Boolean = false
)
