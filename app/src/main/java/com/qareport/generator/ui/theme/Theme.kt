package com.qareport.generator.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestStatus
import com.qareport.generator.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = QaBlue,
    onPrimary = Color.White,
    primaryContainer = QaBlueContainer,
    onPrimaryContainer = QaOnBlueContainer,
    secondary = QaTeal,
    onSecondary = Color.White,
    secondaryContainer = QaTealContainer,
    onSecondaryContainer = Color(0xFF002022),
    background = QaSurfaceLight,
    onBackground = QaOnSurfaceLight,
    surface = Color.White,
    onSurface = QaOnSurfaceLight,
    surfaceVariant = QaSurfaceContainerLight,
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF757780),
    error = StatusFail,
    onError = Color.White,
    errorContainer = StatusFailContainer,
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = QaBlueDark,
    onPrimary = Color(0xFF002E6B),
    primaryContainer = QaBlueContainerDark,
    onPrimaryContainer = QaOnBlueContainerDark,
    secondary = QaTealDark,
    onSecondary = Color(0xFF00363A),
    secondaryContainer = QaTealContainerDark,
    onSecondaryContainer = QaTealContainer,
    background = QaSurfaceDark,
    onBackground = QaOnSurfaceDark,
    surface = QaSurfaceContainerDark,
    onSurface = QaOnSurfaceDark,
    surfaceVariant = Color(0xFF272C33),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = StatusFailContainerDark,
    onErrorContainer = Color(0xFFFFDAD6)
)

/** Colores de estado accesibles desde cualquier composable mediante [QaColors]. */
data class QaStatusColors(
    val pass: Color,
    val fail: Color,
    val blocked: Color,
    val skipped: Color,
    val pending: Color,
    val passContainer: Color,
    val failContainer: Color,
    val blockedContainer: Color,
    val skippedContainer: Color,
    val pendingContainer: Color
) {
    fun colorFor(status: TestStatus): Color = when (status) {
        TestStatus.PASS -> pass
        TestStatus.FAIL -> fail
        TestStatus.BLOCKED -> blocked
        TestStatus.SKIPPED -> skipped
        TestStatus.NOT_TESTED -> pending
    }

    fun containerFor(status: TestStatus): Color = when (status) {
        TestStatus.PASS -> passContainer
        TestStatus.FAIL -> failContainer
        TestStatus.BLOCKED -> blockedContainer
        TestStatus.SKIPPED -> skippedContainer
        TestStatus.NOT_TESTED -> pendingContainer
    }

    fun colorFor(severity: Severity): Color = when (severity) {
        Severity.BLOCKER -> SeverityBlocker
        Severity.CRITICAL -> SeverityCritical
        Severity.MAJOR -> SeverityMajor
        Severity.MINOR -> SeverityMinor
    }

    fun colorFor(status: BugStatus): Color = when (status) {
        BugStatus.OPEN -> fail
        BugStatus.IN_PROGRESS -> blocked
        BugStatus.FIXED -> pass
        BugStatus.RETEST -> skipped
        BugStatus.CLOSED -> pending
        BugStatus.REJECTED -> pending
    }
}

private val LightStatusColors = QaStatusColors(
    pass = StatusPass,
    fail = StatusFail,
    blocked = StatusBlocked,
    skipped = StatusSkipped,
    pending = StatusPending,
    passContainer = StatusPassContainer,
    failContainer = StatusFailContainer,
    blockedContainer = StatusBlockedContainer,
    skippedContainer = StatusSkippedContainer,
    pendingContainer = StatusPendingContainer
)

private val DarkStatusColors = QaStatusColors(
    pass = Color(0xFF6ADF95),
    fail = Color(0xFFFF9E9E),
    blocked = Color(0xFFF3C078),
    skipped = Color(0xFFC4A8FF),
    pending = Color(0xFFA8B3C1),
    passContainer = StatusPassContainerDark,
    failContainer = StatusFailContainerDark,
    blockedContainer = StatusBlockedContainerDark,
    skippedContainer = StatusSkippedContainerDark,
    pendingContainer = StatusPendingContainerDark
)

val LocalQaStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Atajo para leer los colores de estado dentro de un composable. */
object QaColors {
    val status: QaStatusColors
        @Composable get() = LocalQaStatusColors.current
}

@Composable
fun QAReportGeneratorTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CompositionLocalProvider(
        LocalQaStatusColors provides if (useDarkTheme) DarkStatusColors else LightStatusColors
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) DarkColors else LightColors,
            typography = QaTypography,
            content = content
        )
    }
}
