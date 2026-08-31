package com.gastos.quincenal.domain.model

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Importe monetario representado en centavos (enteros).
 *
 * Todo el dinero de la aplicacion viaja como [Money]. Se usa un entero de centavos
 * en lugar de [Double] porque las sumas y restas de dinero con punto flotante
 * acumulan error (0.1 + 0.2 != 0.3) y el requisito principal del proyecto es que
 * los calculos financieros sean exactos.
 */
@JvmInline
value class Money(val cents: Long) : Comparable<Money> {

    val isZero: Boolean get() = cents == 0L
    val isNegative: Boolean get() = cents < 0L
    val isPositive: Boolean get() = cents > 0L

    /** Valor aproximado en pesos; usar SOLO para graficar, nunca para acumular. */
    val asDouble: Double get() = cents / 100.0

    operator fun plus(other: Money) = Money(cents + other.cents)
    operator fun minus(other: Money) = Money(cents - other.cents)
    operator fun times(factor: Int) = Money(cents * factor)
    operator fun unaryMinus() = Money(-cents)

    fun abs() = Money(abs(cents))

    override fun compareTo(other: Money): Int = cents.compareTo(other.cents)

    companion object {
        val ZERO = Money(0L)

        fun ofCents(cents: Long) = Money(cents)

        /** Construye un importe a partir de pesos con decimales (redondeo al centavo). */
        fun ofPesos(pesos: Double) = Money((pesos * 100.0).roundToLong())

        /**
         * Convierte texto escrito por el usuario a [Money].
         *
         * Acepta "1234", "1,234.50", "$1 234.50", "1234,50". Devuelve `null` si el
         * texto no representa una cantidad valida, para que la capa de UI muestre
         * el error de validacion en lugar de guardar basura.
         */
        fun parse(input: String): Money? {
            val cleaned = input.trim()
                .replace("$", "")
                .replace(" ", "")
                .replace(" ", "")
            if (cleaned.isEmpty()) return null

            val negative = cleaned.startsWith("-")
            val body = cleaned.removePrefix("-").removePrefix("+")
            if (body.isEmpty()) return null

            // Se toma el ultimo separador como decimal solo si deja 1 o 2 digitos.
            val lastDot = body.lastIndexOf('.')
            val lastComma = body.lastIndexOf(',')
            val sepIndex = maxOf(lastDot, lastComma)
            val decimalsCount = if (sepIndex >= 0) body.length - sepIndex - 1 else 0
            val hasDecimalSep = sepIndex >= 0 && decimalsCount in 1..2

            val integerPart: String
            val decimalPart: String
            if (hasDecimalSep) {
                integerPart = body.substring(0, sepIndex).replace(",", "").replace(".", "")
                decimalPart = body.substring(sepIndex + 1)
            } else {
                integerPart = body.replace(",", "").replace(".", "")
                decimalPart = ""
            }

            if (integerPart.isNotEmpty() && !integerPart.all { it.isDigit() }) return null
            if (decimalPart.isNotEmpty() && !decimalPart.all { it.isDigit() }) return null
            if (integerPart.isEmpty() && decimalPart.isEmpty()) return null

            val units = if (integerPart.isEmpty()) 0L else integerPart.toLongOrNull() ?: return null
            val cents = when (decimalPart.length) {
                0 -> 0L
                1 -> decimalPart.toLong() * 10
                else -> decimalPart.toLong()
            }
            val total = units * 100 + cents
            return Money(if (negative) -total else total)
        }
    }
}

/** Suma exacta de importes. */
fun Iterable<Money>.sumOfMoney(): Money = Money(sumOf { it.cents })

/** Suma exacta proyectando cada elemento a un importe. */
inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money =
    Money(sumOf { selector(it).cents })
