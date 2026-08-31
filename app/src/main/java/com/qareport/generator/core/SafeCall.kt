package com.qareport.generator.core

import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * Ejecuta un bloque suspendido devolviendo un [Outcome] en lugar de propagar la excepción.
 *
 * [CancellationException] se vuelve a lanzar siempre: cancelar una corrutina no es un
 * error y tragarla rompería la estructura de concurrencia.
 */
suspend fun <T> safeCall(
    errorMessage: String,
    logEvent: String? = null,
    block: suspend () -> T
): Outcome<T> = try {
    Outcome.Success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    if (logEvent != null) {
        AppLogger.errorEvent(logEvent, errorMessage, error)
    } else {
        AppLogger.error(errorMessage, error)
    }
    Outcome.Failure(errorMessage, error)
}
