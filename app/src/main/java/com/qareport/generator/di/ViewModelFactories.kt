package com.qareport.generator.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.qareport.generator.QaApplication

/**
 * Acceso al [AppContainer] desde las factories de los ViewModels.
 * Permite construir los ViewModels con dependencias explícitas sin usar un
 * framework de inyección.
 */
fun CreationExtras.appContainer(): AppContainer {
    val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
        ?: error("No hay Application disponible en CreationExtras")
    return (application as QaApplication).container
}
