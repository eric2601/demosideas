package com.gastos.quincenal.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.gastos.quincenal.data.local.AppDatabase
import com.gastos.quincenal.data.local.entity.UserSettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader

/**
 * Exporta e importa el respaldo completo mediante el Storage Access Framework.
 *
 * El usuario elige el archivo con el selector del sistema, asi que la aplicacion
 * no necesita permisos de almacenamiento ni escribe fuera de donde se le indica.
 */
class BackupRepository(
    private val context: Context,
    private val db: AppDatabase
) {

    suspend fun export(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildPayload()
            val json = BackupSerializer.encode(payload)
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
                output.flush()
            } ?: error("No se pudo abrir el archivo para escritura.")
            BackupResult.Success("Respaldo exportado: ${payload.stats().describe()}.")
        }.getOrElse { error ->
            BackupResult.Failure("No se pudo exportar el respaldo: ${error.message ?: "error desconocido"}")
        }
    }

    /**
     * Reemplaza por completo la informacion local con la del archivo.
     * Todo ocurre dentro de una transaccion: si algo falla, la base queda intacta.
     */
    suspend fun import(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use(BufferedReader::readText)
            } ?: error("No se pudo abrir el archivo para lectura.")

            val payload = BackupSerializer.decode(text)
            require(payload.version <= BackupSerializer.CURRENT_VERSION) {
                "El respaldo fue creado por una version mas reciente de la aplicacion."
            }

            db.withTransaction {
                clearAll()
                db.categoryDao().insertAll(payload.categories)
                db.recurrenceDao().insertAll(payload.recurrences)
                db.expenseDao().insertAll(payload.expenses)
                db.incomeDao().insertAll(payload.incomes)
                db.expenseDao().insertOverrides(payload.expenseOverrides)
                db.incomeDao().insertOverrides(payload.incomeOverrides)
                db.settingsDao().upsertMonths(payload.months)
                db.settingsDao().upsert(payload.settings.copy(id = UserSettingsEntity.SETTINGS_ID))
            }

            BackupResult.Success("Respaldo restaurado: ${payload.stats().describe()}.")
        }.getOrElse { error ->
            BackupResult.Failure("No se pudo importar el respaldo: ${error.message ?: "archivo invalido"}")
        }
    }

    /** Borra toda la informacion del usuario dejando la base lista para empezar de cero. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        db.withTransaction { clearAll() }
    }

    private suspend fun buildPayload(): BackupPayload = BackupPayload(
        version = BackupSerializer.CURRENT_VERSION,
        exportedAt = System.currentTimeMillis(),
        settings = db.settingsDao().getSettings() ?: UserSettingsEntity(),
        categories = db.categoryDao().getAll(),
        recurrences = db.recurrenceDao().getAll(),
        expenses = db.expenseDao().getAll(),
        incomes = db.incomeDao().getAll(),
        expenseOverrides = db.expenseDao().getAllOverrides(),
        incomeOverrides = db.incomeDao().getAllOverrides(),
        months = db.settingsDao().getMonths()
    )

    /** El orden respeta las claves foraneas: primero los hijos, luego los padres. */
    private suspend fun clearAll() {
        db.expenseDao().deleteAllOverrides()
        db.incomeDao().deleteAllOverrides()
        db.expenseDao().deleteAll()
        db.incomeDao().deleteAll()
        db.recurrenceDao().deleteAll()
        db.categoryDao().deleteAll()
        db.settingsDao().deleteAllMonths()
    }

    companion object {
        const val MIME_TYPE = "application/json"

        fun suggestedFileName(): String {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(java.util.Date())
            return "gastos-quincenal-$stamp.json"
        }
    }
}
