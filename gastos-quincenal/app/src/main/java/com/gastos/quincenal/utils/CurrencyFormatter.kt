package com.gastos.quincenal.utils

import com.gastos.quincenal.domain.model.Money
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/**
 * Formateo de importes. Por defecto pesos mexicanos: "$13,600.00".
 *
 * El simbolo y el codigo son configurables (ver Ajustes) para poder usar otra
 * moneda mas adelante sin tocar la UI.
 */
class CurrencyFormatter(
    val symbol: String = DEFAULT_SYMBOL,
    val code: String = DEFAULT_CODE
) {

    private val decimalFormat: DecimalFormat = DecimalFormat(
        "#,##0.00",
        DecimalFormatSymbols(DateUtils.LOCALE).apply {
            groupingSeparator = ','
            decimalSeparator = '.'
        }
    )

    /** "$13,600.00" y "-$778.00" para negativos. */
    fun format(money: Money): String {
        val absText = decimalFormat.format(money.abs().cents / 100.0)
        return if (money.isNegative) "-$symbol$absText" else "$symbol$absText"
    }

    /** "+$13,600.00": usado para los ingresos en las listas. */
    fun formatSigned(money: Money): String =
        if (money.isNegative) format(money) else "+${format(money)}"

    /** "13,600.00" sin simbolo, para campos de edicion. */
    fun formatPlain(money: Money): String {
        val absText = decimalFormat.format(money.abs().cents / 100.0)
        return if (money.isNegative) "-$absText" else absText
    }

    /** "$13,600.00 MXN" */
    fun formatWithCode(money: Money): String = "${format(money)} $code"

    /** Version compacta para etiquetas de graficas: "$13.6k". */
    fun formatCompact(money: Money): String {
        val pesos = money.abs().cents / 100.0
        val sign = if (money.isNegative) "-" else ""
        return when {
            pesos >= 1_000_000 -> "$sign$symbol%.1fM".format(DateUtils.LOCALE, pesos / 1_000_000)
            pesos >= 1_000 -> "$sign$symbol%.1fk".format(DateUtils.LOCALE, pesos / 1_000)
            else -> "$sign$symbol%.0f".format(DateUtils.LOCALE, pesos)
        }
    }

    companion object {
        const val DEFAULT_SYMBOL = "$"
        const val DEFAULT_CODE = "MXN"

        val MXN = CurrencyFormatter()
    }
}
