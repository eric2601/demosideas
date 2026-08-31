package com.qareport.generator.domain.export

/**
 * Modelo JSON mínimo, sin dependencias externas.
 *
 * Se implementa a mano en lugar de usar `org.json` (que en los tests unitarios JVM está
 * stubbeado por el runtime de Android y devolvería valores por defecto) o
 * kotlinx.serialization (dependencia extra + plugin de compilación adicional).
 * De este modo la exportación e importación son Kotlin puro y quedan cubiertas por
 * tests unitarios normales.
 */
sealed interface JsonValue {

    data class Str(val value: String) : JsonValue

    data class Num(val value: Double) : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    data object Null : JsonValue

    data class Arr(val items: List<JsonValue>) : JsonValue

    data class Obj(val entries: List<Pair<String, JsonValue>>) : JsonValue {
        private val index: Map<String, JsonValue> by lazy { entries.toMap() }

        operator fun get(key: String): JsonValue? = index[key]
    }
}

object Json {

    // --- construcción ---

    fun obj(vararg entries: Pair<String, JsonValue>): JsonValue.Obj = JsonValue.Obj(entries.toList())

    fun obj(entries: List<Pair<String, JsonValue>>): JsonValue.Obj = JsonValue.Obj(entries)

    fun arr(items: List<JsonValue>): JsonValue.Arr = JsonValue.Arr(items)

    fun of(text: String?): JsonValue = if (text == null) JsonValue.Null else JsonValue.Str(text)

    fun of(number: Int): JsonValue = JsonValue.Num(number.toDouble())

    fun of(number: Long): JsonValue = JsonValue.Num(number.toDouble())

    fun of(number: Double): JsonValue = JsonValue.Num(if (number.isFinite()) number else 0.0)

    fun of(flag: Boolean): JsonValue = JsonValue.Bool(flag)

    fun ofNullable(number: Long?): JsonValue = if (number == null) JsonValue.Null else of(number)

    // --- serialización ---

    fun stringify(value: JsonValue, indentUnit: String = "  "): String {
        val builder = StringBuilder()
        write(value, builder, indentUnit, 0)
        return builder.toString()
    }

    private fun write(value: JsonValue, out: StringBuilder, indentUnit: String, depth: Int) {
        when (value) {
            is JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            is JsonValue.Num -> out.append(formatNumber(value.value))
            is JsonValue.Str -> {
                out.append('"')
                escapeInto(value.value, out)
                out.append('"')
            }
            is JsonValue.Arr -> {
                if (value.items.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append("[\n")
                value.items.forEachIndexed { index, item ->
                    out.append(indentUnit.repeat(depth + 1))
                    write(item, out, indentUnit, depth + 1)
                    if (index != value.items.lastIndex) out.append(',')
                    out.append('\n')
                }
                out.append(indentUnit.repeat(depth)).append(']')
            }
            is JsonValue.Obj -> {
                if (value.entries.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append("{\n")
                value.entries.forEachIndexed { index, entry ->
                    out.append(indentUnit.repeat(depth + 1)).append('"')
                    escapeInto(entry.first, out)
                    out.append("\": ")
                    write(entry.second, out, indentUnit, depth + 1)
                    if (index != value.entries.lastIndex) out.append(',')
                    out.append('\n')
                }
                out.append(indentUnit.repeat(depth)).append('}')
            }
        }
    }

    /** Los enteros se escriben sin parte decimal para que el JSON sea legible. */
    private fun formatNumber(value: Double): String {
        if (!value.isFinite()) return "0"
        return if (value == Math.floor(value) && Math.abs(value) < 9.007199254740992E15) {
            value.toLong().toString()
        } else {
            java.math.BigDecimal(value)
                .setScale(4, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
        }
    }

    /**
     * Escapa una cadena según RFC 8259. Todo carácter de control (incluido el form feed)
     * se emite en su forma \\uXXXX, por lo que el JSON resultante es siempre válido.
     */
    fun escapeInto(raw: String, out: StringBuilder) {
        for (char in raw) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (char < ' ') {
                    out.append(String.format("\\u%04x", char.code))
                } else {
                    out.append(char)
                }
            }
        }
    }

    fun escape(raw: String): String = StringBuilder(raw.length + 16).also { escapeInto(raw, it) }.toString()

    // --- lectura segura de valores ---

    fun string(value: JsonValue?, fallback: String = ""): String = when (value) {
        is JsonValue.Str -> value.value
        is JsonValue.Num -> formatNumber(value.value)
        is JsonValue.Bool -> value.value.toString()
        else -> fallback
    }

    fun long(value: JsonValue?, fallback: Long = 0L): Long = when (value) {
        is JsonValue.Num -> value.value.toLong()
        is JsonValue.Str -> value.value.trim().toLongOrNull() ?: fallback
        else -> fallback
    }

    fun longOrNull(value: JsonValue?): Long? = when (value) {
        is JsonValue.Num -> value.value.toLong()
        is JsonValue.Str -> value.value.trim().toLongOrNull()
        else -> null
    }

    fun int(value: JsonValue?, fallback: Int = 0): Int = long(value, fallback.toLong()).toInt()

    fun double(value: JsonValue?, fallback: Double = 0.0): Double = when (value) {
        is JsonValue.Num -> value.value
        is JsonValue.Str -> value.value.trim().toDoubleOrNull() ?: fallback
        else -> fallback
    }

    fun bool(value: JsonValue?, fallback: Boolean = false): Boolean = when (value) {
        is JsonValue.Bool -> value.value
        is JsonValue.Str -> value.value.equals("true", ignoreCase = true)
        else -> fallback
    }

    fun array(value: JsonValue?): List<JsonValue> = (value as? JsonValue.Arr)?.items ?: emptyList()

    fun objects(value: JsonValue?): List<JsonValue.Obj> = array(value).filterIsInstance<JsonValue.Obj>()
}
