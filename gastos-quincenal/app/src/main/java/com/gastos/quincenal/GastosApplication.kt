package com.gastos.quincenal

import android.app.Application
import com.gastos.quincenal.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Punto de entrada de la aplicacion; crea el contenedor de dependencias. */
class GastosApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Siembra idempotente: garantiza preferencias y categorias iniciales.
        applicationScope.launch {
            container.settingsRepository.ensureInitialized()
        }
    }
}
