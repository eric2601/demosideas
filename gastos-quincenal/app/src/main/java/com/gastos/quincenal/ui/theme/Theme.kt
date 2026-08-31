package com.gastos.quincenal.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.gastos.quincenal.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = SkyBlue40,
    onPrimary = Color.White,
    primaryContainer = SkyBlue90,
    onPrimaryContainer = SkyBlue10,
    secondary = Orange40,
    onSecondary = Color.White,
    secondaryContainer = Orange90,
    onSecondaryContainer = Orange10,
    tertiary = SkyBlue30,
    onTertiary = Color.White,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral95,
    onSurfaceVariant = Neutral30,
    outline = Neutral80,
    error = NegativeRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = SkyBlue80,
    onPrimary = SkyBlue10,
    primaryContainer = SkyBlue30,
    onPrimaryContainer = SkyBlue90,
    secondary = Orange80,
    onSecondary = Orange10,
    secondaryContainer = Orange30,
    onSecondaryContainer = Orange90,
    tertiary = SkyBlue60,
    onTertiary = SkyBlue10,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral20,
    onSurfaceVariant = Neutral80,
    outline = Neutral30,
    error = NegativeRedLight,
    onError = Neutral10
)

/**
 * Tema de la aplicacion. Respeta la preferencia guardada por el usuario
 * (claro, oscuro o la del sistema) y publica los colores de quincena.
 */
@Composable
fun GastosTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extendedColors = if (darkTheme) QuincenaColors.Dark else QuincenaColors.Light

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalQuincenaColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
