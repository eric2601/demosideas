package com.qareport.generator.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta corporativa. Se define de forma explícita (en lugar de usar colores dinámicos
 * de Material You) para que el reporte y la aplicación mantengan la misma identidad en
 * cualquier dispositivo, algo esperado en una herramienta empresarial.
 */

// Claro
val QaBlue = Color(0xFF0B3D91)
val QaBlueLight = Color(0xFF3A63B8)
val QaBlueContainer = Color(0xFFDCE6FF)
val QaOnBlueContainer = Color(0xFF001A47)
val QaTeal = Color(0xFF00696E)
val QaTealContainer = Color(0xFFB0ECEF)
val QaSurfaceLight = Color(0xFFF7F9FC)
val QaSurfaceContainerLight = Color(0xFFEDF1F7)
val QaOnSurfaceLight = Color(0xFF181C22)

// Oscuro
val QaBlueDark = Color(0xFFAEC7FF)
val QaBlueContainerDark = Color(0xFF1B3E7E)
val QaOnBlueContainerDark = Color(0xFFD9E2FF)
val QaTealDark = Color(0xFF4EDADF)
val QaTealContainerDark = Color(0xFF004F53)
val QaSurfaceDark = Color(0xFF101418)
val QaSurfaceContainerDark = Color(0xFF1B1F24)
val QaOnSurfaceDark = Color(0xFFE2E6EC)

// Estados de prueba (idénticos a los del PDF para que reporte y app coincidan)
val StatusPass = Color(0xFF15803D)
val StatusFail = Color(0xFFB91C1C)
val StatusBlocked = Color(0xFFB45309)
val StatusSkipped = Color(0xFF6D28D9)
val StatusPending = Color(0xFF64748B)

val StatusPassContainer = Color(0xFFD7F5E0)
val StatusFailContainer = Color(0xFFFBDDDD)
val StatusBlockedContainer = Color(0xFFFBEBD2)
val StatusSkippedContainer = Color(0xFFE9DEFB)
val StatusPendingContainer = Color(0xFFE2E8F0)

val StatusPassContainerDark = Color(0xFF13351F)
val StatusFailContainerDark = Color(0xFF3B1717)
val StatusBlockedContainerDark = Color(0xFF3A2A0E)
val StatusSkippedContainerDark = Color(0xFF261A3D)
val StatusPendingContainerDark = Color(0xFF232B36)

// Severidades
val SeverityBlocker = Color(0xFF7F1D1D)
val SeverityCritical = Color(0xFFB91C1C)
val SeverityMajor = Color(0xFFB45309)
val SeverityMinor = Color(0xFF0369A1)
