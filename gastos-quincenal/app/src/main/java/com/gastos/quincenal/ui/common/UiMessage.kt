package com.gastos.quincenal.ui.common

/** Mensaje efimero para la barra inferior; se consume una sola vez. */
data class UiMessage(val id: Long, val text: String) {
    companion object {
        fun of(text: String) = UiMessage(System.nanoTime(), text)
    }
}
