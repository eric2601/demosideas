package com.qareport.generator.core

/**
 * Resultado de una operación que puede fallar.
 *
 * Se prefiere devolver un [Outcome] en lugar de propagar excepciones: la UI decide
 * qué mensaje mostrar y la aplicación nunca se cierra por un error de datos o de E/S.
 */
sealed interface Outcome<out T> {

    data class Success<T>(val data: T) : Outcome<T>

    data class Failure(val message: String, val cause: Throwable? = null) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.data

    fun errorMessageOrNull(): String? = (this as? Failure)?.message
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(data))
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(action: (T) -> Unit): Outcome<T> {
    if (this is Outcome.Success) action(data)
    return this
}

inline fun <T> Outcome<T>.onFailure(action: (String) -> Unit): Outcome<T> {
    if (this is Outcome.Failure) action(message)
    return this
}
