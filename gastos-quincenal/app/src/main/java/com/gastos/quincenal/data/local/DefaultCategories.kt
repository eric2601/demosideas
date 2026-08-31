package com.gastos.quincenal.data.local

import com.gastos.quincenal.data.local.entity.CategoryEntity

/**
 * Categorias sembradas la primera vez que se abre la aplicacion.
 * El usuario puede editarlas, borrarlas y crear las suyas.
 */
object DefaultCategories {

    private fun color(hex: String): Int = hex.removePrefix("#").toLong(16).toInt()

    val ALL: List<CategoryEntity> = listOf(
        CategoryEntity(name = "Sueldo", colorArgb = color("FF2E7D32"), forIncome = true, isDefault = true),
        CategoryEntity(name = "Otros ingresos", colorArgb = color("FF00897B"), forIncome = true, isDefault = true),
        CategoryEntity(name = "Comida", colorArgb = color("FFEF6C00"), isDefault = true),
        CategoryEntity(name = "Gasolina", colorArgb = color("FFD84315"), isDefault = true),
        CategoryEntity(name = "Transporte", colorArgb = color("FF5E35B1"), isDefault = true),
        CategoryEntity(name = "Servicios", colorArgb = color("FF0277BD"), isDefault = true),
        CategoryEntity(name = "Electronica", colorArgb = color("FF00838F"), isDefault = true),
        CategoryEntity(name = "Deudas", colorArgb = color("FFC62828"), isDefault = true),
        CategoryEntity(name = "Familia", colorArgb = color("FF6D4C41"), isDefault = true),
        CategoryEntity(name = "Entretenimiento", colorArgb = color("FFAD1457"), isDefault = true),
        CategoryEntity(name = "Renta", colorArgb = color("FF37474F"), isDefault = true),
        CategoryEntity(name = "Salud", colorArgb = color("FF2E7D32"), isDefault = true),
        CategoryEntity(name = "Otros", colorArgb = color("FF757575"), isDefault = true)
    )

    /** Color usado cuando un gasto se quedo sin categoria. */
    val FALLBACK_COLOR: Int = color("FF9E9E9E")
    const val FALLBACK_NAME = "Sin categoria"
}
