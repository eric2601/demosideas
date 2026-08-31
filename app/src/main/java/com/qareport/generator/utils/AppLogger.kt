package com.qareport.generator.utils

import android.util.Log

/**
 * Registro técnico centralizado.
 *
 * Reglas:
 *  - Solo se registran identificadores y contadores; NUNCA contenido de evidencias,
 *    rutas completas de archivos del usuario ni datos personales.
 *  - Los errores siempre incluyen la excepción para que aparezca el stack trace
 *    completo en Logcat.
 */
object AppLogger {

    private const val TAG = "QAReport"

    /** Eventos técnicos con nombre estable, para poder filtrarlos en Logcat. */
    object Event {
        const val APP_START = "APP_START"
        const val PROJECT_CREATED = "PROJECT_CREATED"
        const val PROJECT_UPDATED = "PROJECT_UPDATED"
        const val PROJECT_DELETED = "PROJECT_DELETED"
        const val PROJECT_FINALIZED = "PROJECT_FINALIZED"
        const val TEST_CREATED = "TEST_CREATED"
        const val TEST_UPDATED = "TEST_UPDATED"
        const val TEST_DELETED = "TEST_DELETED"
        const val TEST_EXECUTED = "TEST_EXECUTED"
        const val EVIDENCE_ADDED = "EVIDENCE_ADDED"
        const val EVIDENCE_DELETED = "EVIDENCE_DELETED"
        const val LOG_SAVED = "LOG_SAVED"
        const val BUG_CREATED = "BUG_CREATED"
        const val BUG_UPDATED = "BUG_UPDATED"
        const val REPORT_GENERATION_STARTED = "REPORT_GENERATION_STARTED"
        const val REPORT_GENERATION_SUCCESS = "REPORT_GENERATION_SUCCESS"
        const val REPORT_GENERATION_ERROR = "REPORT_GENERATION_ERROR"
        const val EXPORT_STARTED = "EXPORT_STARTED"
        const val EXPORT_SUCCESS = "EXPORT_SUCCESS"
        const val EXPORT_ERROR = "EXPORT_ERROR"
        const val DEMO_DATA_LOADED = "DEMO_DATA_LOADED"
        const val SHARE_REQUESTED = "SHARE_REQUESTED"
    }

    fun event(event: String, detail: String = "") {
        if (detail.isEmpty()) {
            Log.i(TAG, event)
        } else {
            Log.i(TAG, "$event | $detail")
        }
    }

    fun debug(message: String) {
        Log.d(TAG, message)
    }

    fun warn(message: String, error: Throwable? = null) {
        if (error == null) Log.w(TAG, message) else Log.w(TAG, message, error)
    }

    fun error(message: String, error: Throwable? = null) {
        if (error == null) Log.e(TAG, message) else Log.e(TAG, message, error)
    }

    fun errorEvent(event: String, message: String, error: Throwable?) {
        Log.e(TAG, "$event | $message", error)
    }
}
