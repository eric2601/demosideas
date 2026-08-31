package com.qareport.generator.ui.common

/**
 * Mensaje efímero para la barra inferior (Snackbar).
 * El [id] permite volver a mostrar el mismo texto dos veces seguidas.
 */
data class UiMessage(
    val id: Long,
    val text: String,
    val isError: Boolean = false
)
