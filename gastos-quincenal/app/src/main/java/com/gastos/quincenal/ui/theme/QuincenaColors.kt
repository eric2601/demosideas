package com.gastos.quincenal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.gastos.quincenal.domain.model.Quincena

/**
 * Colores propios del dominio que Material 3 no cubre: el par azul cielo /
 * naranja de las quincenas y los colores de saldo positivo o negativo.
 */
@Immutable
data class QuincenaColors(
    val firstContainer: Color,
    val firstOnContainer: Color,
    val firstAccent: Color,
    val secondContainer: Color,
    val secondOnContainer: Color,
    val secondAccent: Color,
    val positive: Color,
    val negative: Color,
    val caution: Color
) {
    fun container(quincena: Quincena): Color =
        if (quincena == Quincena.PRIMERA) firstContainer else secondContainer

    fun onContainer(quincena: Quincena): Color =
        if (quincena == Quincena.PRIMERA) firstOnContainer else secondOnContainer

    fun accent(quincena: Quincena): Color =
        if (quincena == Quincena.PRIMERA) firstAccent else secondAccent

    /** Verde si sobra dinero, rojo si falta. */
    fun forBalance(isNegative: Boolean): Color = if (isNegative) negative else positive

    companion object {
        val Light = QuincenaColors(
            firstContainer = SkyBlue95,
            firstOnContainer = SkyBlue10,
            firstAccent = SkyBlue40,
            secondContainer = Orange95,
            secondOnContainer = Orange10,
            secondAccent = Orange40,
            positive = PositiveGreen,
            negative = NegativeRed,
            caution = CautionAmber
        )

        val Dark = QuincenaColors(
            firstContainer = SkyBlue20,
            firstOnContainer = SkyBlue90,
            firstAccent = SkyBlue60,
            secondContainer = Orange20,
            secondOnContainer = Orange90,
            secondAccent = Orange60,
            positive = PositiveGreenLight,
            negative = NegativeRedLight,
            caution = CautionAmberLight
        )
    }
}

/** Acceso a los colores de quincena desde cualquier composable. */
val quincenaColors: QuincenaColors
    @Composable
    @ReadOnlyComposable
    get() = LocalQuincenaColors.current

internal val LocalQuincenaColors = androidx.compose.runtime.staticCompositionLocalOf { QuincenaColors.Light }

/** Color de texto legible sobre el contenedor de una quincena. */
@Composable
@ReadOnlyComposable
fun onSurfaceMuted(): Color = MaterialTheme.colorScheme.onSurfaceVariant
