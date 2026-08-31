package com.qareport.generator.domain.export

/**
 * Parser JSON recursivo mínimo, complementario a [Json], usado para restaurar
 * un respaldo de proyecto generado por la propia aplicación.
 *
 * Nunca lanza excepciones hacia el llamador: [parseOrNull] devuelve null ante
 * cualquier entrada malformada, de acuerdo con el requisito de estabilidad.
 */
object JsonParser {

    private class ParseException(message: String) : RuntimeException(message)

    private const val MAX_DEPTH = 64

    fun parseOrNull(text: String): JsonValue? = try {
        val cursor = Cursor(text)
        cursor.skipWhitespace()
        val value = cursor.readValue(0)
        cursor.skipWhitespace()
        if (!cursor.isAtEnd) null else value
    } catch (error: ParseException) {
        null
    } catch (error: IndexOutOfBoundsException) {
        null
    }

    fun parseObjectOrNull(text: String): JsonValue.Obj? = parseOrNull(text) as? JsonValue.Obj

    private class Cursor(private val source: String) {
        private var position = 0

        val isAtEnd: Boolean get() = position >= source.length

        fun skipWhitespace() {
            while (position < source.length && source[position].isWhitespace()) position++
        }

        fun readValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) throw ParseException("Profundidad máxima excedida")
            skipWhitespace()
            if (isAtEnd) throw ParseException("Fin de entrada inesperado")
            return when (source[position]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't' -> { readLiteral("true"); JsonValue.Bool(true) }
                'f' -> { readLiteral("false"); JsonValue.Bool(false) }
                'n' -> { readLiteral("null"); JsonValue.Null }
                else -> readNumber()
            }
        }

        private fun readObject(depth: Int): JsonValue.Obj {
            expect('{')
            val entries = mutableListOf<Pair<String, JsonValue>>()
            skipWhitespace()
            if (peek() == '}') {
                position++
                return JsonValue.Obj(entries)
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                val value = readValue(depth + 1)
                entries += key to value
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return JsonValue.Obj(entries)
                    }
                    else -> throw ParseException("Se esperaba ',' o '}'")
                }
            }
        }

        private fun readArray(depth: Int): JsonValue.Arr {
            expect('[')
            val items = mutableListOf<JsonValue>()
            skipWhitespace()
            if (peek() == ']') {
                position++
                return JsonValue.Arr(items)
            }
            while (true) {
                items += readValue(depth + 1)
                skipWhitespace()
                when (peek()) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return JsonValue.Arr(items)
                    }
                    else -> throw ParseException("Se esperaba ',' o ']'")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                if (isAtEnd) throw ParseException("Cadena sin cerrar")
                when (val char = source[position++]) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (isAtEnd) throw ParseException("Escape incompleto")
                        when (source[position++]) {
                            '"' -> builder.append('"')
                            '\\' -> builder.append('\\')
                            '/' -> builder.append('/')
                            'b' -> builder.append('\b')
                            'f' -> builder.append('\u000C')
                            'n' -> builder.append('\n')
                            'r' -> builder.append('\r')
                            't' -> builder.append('\t')
                            'u' -> {
                                if (position + 4 > source.length) throw ParseException("Escape unicode incompleto")
                                val hex = source.substring(position, position + 4)
                                val code = hex.toIntOrNull(16) ?: throw ParseException("Escape unicode inválido")
                                builder.append(code.toChar())
                                position += 4
                            }
                            else -> throw ParseException("Escape desconocido")
                        }
                    }
                    else -> builder.append(char)
                }
            }
        }

        private fun readNumber(): JsonValue.Num {
            val start = position
            if (peek() == '-' || peek() == '+') position++
            while (!isAtEnd) {
                val char = source[position]
                if (char.isDigit() || char == '.' || char == 'e' || char == 'E' || char == '-' || char == '+') {
                    position++
                } else {
                    break
                }
            }
            val raw = source.substring(start, position)
            val number = raw.toDoubleOrNull() ?: throw ParseException("Número inválido")
            return JsonValue.Num(number)
        }

        private fun readLiteral(literal: String) {
            if (position + literal.length > source.length ||
                source.substring(position, position + literal.length) != literal
            ) {
                throw ParseException("Literal inválido")
            }
            position += literal.length
        }

        private fun peek(): Char {
            if (isAtEnd) throw ParseException("Fin de entrada inesperado")
            return source[position]
        }

        private fun expect(expected: Char) {
            if (peek() != expected) throw ParseException("Carácter inesperado")
            position++
        }
    }
}
