package com.qareport.generator.ui.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qareport.generator.core.Outcome
import com.qareport.generator.data.repository.LogRepository
import com.qareport.generator.di.appContainer
import com.qareport.generator.domain.export.TxtExporter
import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.domain.util.DateFormats
import com.qareport.generator.domain.util.TextUtils
import com.qareport.generator.ui.common.BaseViewModel
import com.qareport.generator.utils.AppLogger
import com.qareport.generator.utils.FileStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/**
 * Registro de logs del proyecto.
 *
 * El usuario pega o adjunta el contenido manualmente: la aplicación NO ejecuta comandos
 * ADB ni lee el logcat del sistema por su cuenta.
 */
class LogsViewModel(
    private val projectId: Long,
    private val logRepository: LogRepository,
    private val fileStorage: FileStorage
) : BaseViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _draftTitle = MutableStateFlow("")
    val draftTitle: StateFlow<String> = _draftTitle.asStateFlow()

    private val _draftContent = MutableStateFlow("")
    val draftContent: StateFlow<String> = _draftContent.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val allLogs: StateFlow<List<LogEntry>> = logRepository.observeByProject(projectId)
        .catch { error ->
            AppLogger.error("Fallo al observar logs", error)
            showError("No se pudieron cargar los logs.")
            emit(emptyList())
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
            emptyList()
        )

    /** Logs filtrados por texto, buscando tanto en el título como en el contenido. */
    val logs: StateFlow<List<LogEntry>> = combine(allLogs, _query) { list, query ->
        val term = query.trim()
        if (term.isEmpty()) {
            list
        } else {
            list.filter {
                it.title.contains(term, ignoreCase = true) || it.content.contains(term, ignoreCase = true)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(DashboardViewModel.STOP_TIMEOUT_MS),
        emptyList()
    )

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onDraftTitleChange(value: String) {
        _draftTitle.value = value
    }

    fun onDraftContentChange(value: String) {
        _draftContent.value = value
    }

    fun saveDraft() {
        val content = _draftContent.value
        if (content.isBlank()) {
            showError("El log no puede estar vacío.")
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val entry = LogEntry(
                projectId = projectId,
                title = _draftTitle.value.trim().ifBlank { "Log ${DateFormats.formatDateTime(System.currentTimeMillis())}" },
                content = content.trim()
            )
            val saved = logRepository.save(entry)
            _isSaving.value = false
            if (handle(saved, "Log guardado.")) {
                _draftTitle.value = ""
                _draftContent.value = ""
            }
        }
    }

    /** Carga un archivo de texto elegido por el usuario en el borrador. */
    fun loadFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            val text = fileStorage.readTextFrom(uri)
            if (text == null) {
                showError("No se pudo leer el archivo seleccionado.")
                return@launch
            }
            _draftContent.value = text.take(MAX_IMPORT_CHARS)
            if (_draftTitle.value.isBlank()) {
                _draftTitle.value = fileStorage.queryDisplayName(uri)
            }
            if (text.length > MAX_IMPORT_CHARS) {
                showMessage("El archivo se truncó a $MAX_IMPORT_CHARS caracteres.")
            }
        }
    }

    /** Exporta un log a TXT dentro del almacenamiento de la app y devuelve el archivo. */
    fun exportToTxt(entry: LogEntry, onExported: (File) -> Unit) {
        viewModelScope.launch {
            val fileName = "LOG_${TextUtils.toSafeFileName(entry.title, "log")}_" +
                "${DateFormats.fileTimestamp()}.txt"
            val file = fileStorage.writeExport(fileName, TxtExporter.exportLog(entry))
            if (file == null) {
                showError("No se pudo exportar el log.")
            } else {
                showMessage("Log exportado: ${file.name}")
                onExported(file)
            }
        }
    }

    fun delete(entry: LogEntry) {
        viewModelScope.launch {
            when (val outcome = logRepository.delete(entry.id)) {
                is Outcome.Success -> showMessage("Log eliminado.")
                is Outcome.Failure -> showError(outcome.message)
            }
        }
    }

    companion object {
        /** Límite de importación para no bloquear la UI con archivos enormes. */
        private const val MAX_IMPORT_CHARS = 200_000

        fun factory(projectId: Long) = viewModelFactory {
            initializer {
                val container = appContainer()
                LogsViewModel(projectId, container.logRepository, container.fileStorage)
            }
        }
    }
}
