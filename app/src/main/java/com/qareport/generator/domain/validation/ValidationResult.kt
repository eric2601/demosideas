package com.qareport.generator.domain.validation

/**
 * Resultado de una validación. Se prefiere devolver errores tipados en vez de lanzar
 * excepciones: la UI muestra los mensajes y la app nunca se cierra por una entrada inválida.
 */
sealed interface ValidationResult {

    data object Valid : ValidationResult

    data class Invalid(val errors: List<ValidationError>) : ValidationResult

    val isValid: Boolean get() = this is Valid

    /** Mensaje del primer error, o null si la validación fue correcta. */
    val firstMessage: String?
        get() = (this as? Invalid)?.errors?.firstOrNull()?.message

    val messages: List<String>
        get() = (this as? Invalid)?.errors?.map { it.message } ?: emptyList()

    /** Mensaje asociado a un campo concreto, para mostrarlo bajo el TextField. */
    fun messageFor(field: String): String? =
        (this as? Invalid)?.errors?.firstOrNull { it.field == field }?.message

    companion object {
        fun of(errors: List<ValidationError>): ValidationResult =
            if (errors.isEmpty()) Valid else Invalid(errors)
    }
}

data class ValidationError(
    /** Nombre del campo afectado, o cadena vacía si el error es del formulario completo. */
    val field: String,
    val message: String
)
