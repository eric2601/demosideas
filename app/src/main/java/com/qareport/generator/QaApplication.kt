package com.qareport.generator

import android.app.Application
import com.qareport.generator.di.AppContainer
import com.qareport.generator.utils.AppLogger

/**
 * Punto de entrada de la aplicación. Crea el contenedor de dependencias y limpia la
 * caché de fotos temporales que hayan quedado de capturas canceladas.
 */
class QaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppLogger.event(AppLogger.Event.APP_START)
        runCatching { container.fileStorage.clearCameraCache() }
            .onFailure { AppLogger.warn("No se pudo limpiar la caché de cámara al iniciar", it) }
    }
}
