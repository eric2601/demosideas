package com.qareport.generator.ui.common

import androidx.lifecycle.ViewModel
import com.qareport.generator.core.Outcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base común de los ViewModels: expone un canal de mensajes para la UI y utilidades
 * para traducir un [Outcome] fallido en un mensaje visible sin propagar excepciones.
 */
abstract class BaseViewModel : ViewModel() {

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private var messageCounter = 0L

    protected fun showMessage(text: String) {
        messageCounter++
        _message.value = UiMessage(messageCounter, text, isError = false)
    }

    protected fun showError(text: String) {
        messageCounter++
        _message.value = UiMessage(messageCounter, text, isError = true)
    }

    /** Muestra el error del [Outcome] y devuelve true si la operación fue correcta. */
    protected fun <T> handle(outcome: Outcome<T>, successMessage: String? = null): Boolean =
        when (outcome) {
            is Outcome.Success -> {
                successMessage?.let { showMessage(it) }
                true
            }
            is Outcome.Failure -> {
                showError(outcome.message)
                false
            }
        }

    fun consumeMessage() {
        _message.value = null
    }
}
