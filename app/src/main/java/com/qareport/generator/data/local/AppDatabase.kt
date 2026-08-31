package com.qareport.generator.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.qareport.generator.data.local.dao.BugDao
import com.qareport.generator.data.local.dao.EvidenceDao
import com.qareport.generator.data.local.dao.LogEntryDao
import com.qareport.generator.data.local.dao.ProjectDao
import com.qareport.generator.data.local.dao.ReportDao
import com.qareport.generator.data.local.dao.TestCaseDao
import com.qareport.generator.data.local.dao.TestExecutionDao
import com.qareport.generator.data.local.entity.BugEntity
import com.qareport.generator.data.local.entity.EvidenceEntity
import com.qareport.generator.data.local.entity.LogEntryEntity
import com.qareport.generator.data.local.entity.ProjectEntity
import com.qareport.generator.data.local.entity.ReportEntity
import com.qareport.generator.data.local.entity.TestCaseEntity
import com.qareport.generator.data.local.entity.TestExecutionEntity

/**
 * Base de datos local de la aplicación.
 *
 * Los esquemas se exportan a `app/schemas` (configurado en build.gradle.kts) para poder
 * escribir migraciones reales cuando cambie la versión; por eso NO se usa
 * `fallbackToDestructiveMigration`, que borraría los datos del tester en una
 * actualización.
 */
@Database(
    entities = [
        ProjectEntity::class,
        TestCaseEntity::class,
        TestExecutionEntity::class,
        BugEntity::class,
        EvidenceEntity::class,
        LogEntryEntity::class,
        ReportEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    abstract fun testCaseDao(): TestCaseDao

    abstract fun testExecutionDao(): TestExecutionDao

    abstract fun bugDao(): BugDao

    abstract fun evidenceDao(): EvidenceDao

    abstract fun logEntryDao(): LogEntryDao

    abstract fun reportDao(): ReportDao

    companion object {
        const val DATABASE_NAME = "qa_report_generator.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Room activa las claves foráneas por defecto, pero se refuerza
                        // explícitamente para garantizar el borrado en cascada.
                        db.execSQL("PRAGMA foreign_keys = ON")
                    }
                })
                .build()

        /** Solo para tests instrumentados: base en memoria, sin persistencia. */
        fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
