package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.data.repository.ReportRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Report
import com.qareport.generator.ui.common.BaseViewModel
import com.qareport.generator.utils.AppLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Historial de reportes y exportaciones generados por la aplicación. */
class ReportViewModel(
    private val reportRepository: ReportRepository
) : BaseViewModel() {

    val reports: StateFlow<List<Report>> = reportRepository.observeAll()
        .catch { error ->
            AppLogger.error("Fallo al observar el historial de reportes", error)
            showError("No se pudo cargar el historial de reportes.")
            emit(emptyList())
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
            emptyList()
        )

    /** Devuelve el archivo solo si sigue existiendo en disco. */
    fun fileFor(report: Report): File? = reportRepository.fileFor(report)

    fun delete(report: Report) {
        viewModelScope.launch {
            handle(reportRepository.delete(report), successMessage = "Reporte eliminado.")
        }
    }

    fun rename(report: Report, newName: String) {
        if (newName.isBlank()) {
            showError("El nombre no puede estar vacío.")
            return
        }
        viewModelScope.launch {
            handle(reportRepository.rename(report, newName), successMessage = "Reporte renombrado.")
        }
    }

    fun reportMissing() {
        showError("El archivo ya no está disponible en el dispositivo.")
    }

    fun noAppAvailable() {
        showError("No hay ninguna aplicación instalada capaz de abrir este archivo.")
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { ReportViewModel(appContainer().reportRepository) }
        }
    }
}
