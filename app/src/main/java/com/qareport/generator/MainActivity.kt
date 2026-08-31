package com.qareport.generator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qareport.generator.ui.navigation.QaNavHost
import com.qareport.generator.ui.theme.QAReportGeneratorTheme
import com.qareport.generator.ui.viewmodel.ThemeViewModel
import com.qareport.generator.utils.AppLogger

/**
 * Única Activity de la aplicación: todo el contenido es Jetpack Compose y la
 * navegación se resuelve con navigation-compose.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val themeViewModel: ThemeViewModel = viewModel(factory = ThemeViewModel.Factory)
            val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()

            QAReportGeneratorTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    QaNavHost(appVersion = appVersionLabel())
                }
            }
        }
    }

    /** Versión mostrada en la pantalla de configuración. */
    private fun appVersionLabel(): String = try {
        "v${BuildConfig.VERSION_NAME}"
    } catch (error: Exception) {
        AppLogger.warn("No se pudo leer la versión de la aplicación", error)
        ""
    }
}
