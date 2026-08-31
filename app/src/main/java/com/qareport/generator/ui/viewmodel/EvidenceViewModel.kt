package com.qareport.generator.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.data.repository.EvidenceRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.model.Evidence
import com.qareport.generator.ui.common.BaseViewModel
import com.qareport.generator.utils.AppLogger
import com.qareport.generator.utils.FileStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Evidencias de un proyecto o de un caso concreto.
 *
 * [testCaseId] es 0 cuando la pantalla muestra las evidencias de todo el proyecto.
 */
class EvidenceViewModel(
    private val projectId: Long,
    private val testCaseId: Long,
    private val evidenceRepository: EvidenceRepository,
    private val fileStorage: FileStorage
) : BaseViewModel() {

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    /** Archivo temporal de la foto en curso; se conserva entre el intent y el resultado. */
    private var pendingCameraFile: File? = null

    val evidences: StateFlow<List<Evidence>> =
        (
            if (testCaseId > 0L) {
                evidenceRepository.observeByTestCase(testCaseId)
            } else {
                evidenceRepository.observeByProject(projectId)
            }
            )
            .catch { error ->
                AppLogger.error("Fallo al observar evidencias", error)
                showError("No se pudieron cargar las evidencias.")
                emit(emptyList())
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
                emptyList()
            )

    /** Prepara el archivo destino y devuelve la URI que se pasa al intent de cámara. */
    fun prepareCameraCapture(): Uri? = try {
        val file = fileStorage.createCameraFile()
        pendingCameraFile = file
        fileStorage.shareUriFor(file)
    } catch (error: Exception) {
        AppLogger.error("No se pudo preparar la captura de foto", error)
        showError("No se pudo abrir la cámara.")
        pendingCameraFile = null
        null
    }

    fun onCameraResult(success: Boolean) {
        val file = pendingCameraFile
        pendingCameraFile = null
        if (!success || file == null) {
            file?.delete()
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            handle(
                evidenceRepository.attachCameraPhoto(file, projectId, testCaseIdOrNull()),
                successMessage = "Fotografía agregada."
            )
            _isBusy.value = false
        }
    }

    fun attachFromUri(uri: Uri) {
        viewModelScope.launch {
            _isBusy.value = true
            handle(
                evidenceRepository.attachFromUri(uri, projectId, testCaseIdOrNull()),
                successMessage = "Evidencia adjuntada."
            )
            _isBusy.value = false
        }
    }

    fun attachTextEvidence(content: String, fileName: String) {
        if (content.isBlank()) {
            showError("El contenido no puede estar vacío.")
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            handle(
                evidenceRepository.attachText(content, fileName, projectId, testCaseIdOrNull()),
                successMessage = "Evidencia de texto guardada."
            )
            _isBusy.value = false
        }
    }

    fun delete(evidence: Evidence) {
        viewModelScope.launch {
            handle(evidenceRepository.delete(evidence), successMessage = "Evidencia eliminada.")
        }
    }

    fun fileFor(evidence: Evidence): File? =
        File(evidence.uri).takeIf { it.exists() }

    private fun testCaseIdOrNull(): Long? = testCaseId.takeIf { it > 0L }

    companion object {
        fun factory(projectId: Long, testCaseId: Long) = viewModelFactory {
            initializer {
                val container = appContainer()
                EvidenceViewModel(
                    projectId = projectId,
                    testCaseId = testCaseId,
                    evidenceRepository = container.evidenceRepository,
                    fileStorage = container.fileStorage
                )
            }
        }
    }
}
