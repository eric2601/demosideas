package com.qareport.generator.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qareport.generator.data.local.AppDatabase
import com.qareport.generator.data.mapper.toDomain
import com.qareport.generator.data.mapper.toEntity
import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.Evidence
import com.qareport.generator.domain.model.EvidenceType
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestExecution
import com.qareport.generator.domain.model.TestStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas instrumentadas de Room: verifican el esquema real, las claves foráneas
 * y el borrado en cascada sobre una base en memoria.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = AppDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertProject(name: String = "Terminal P630 QA"): Long {
        val now = System.currentTimeMillis()
        return database.projectDao().insert(
            Project(name = name, version = "1.2.8", tester = "Eric", createdAt = now, updatedAt = now).toEntity()
        )
    }

    private suspend fun insertCase(projectId: Long, code: String, status: TestStatus): Long {
        val now = System.currentTimeMillis()
        return database.testCaseDao().insert(
            TestCase(
                projectId = projectId,
                code = code,
                title = "Caso $code",
                module = "General",
                priority = Priority.HIGH,
                expectedResult = "OK",
                status = status,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        )
    }

    @Test
    fun insertaYRecuperaUnProyecto() = runBlocking {
        val id = insertProject()

        val stored = database.projectDao().getById(id)?.toDomain()

        assertNotNull(stored)
        assertEquals("Terminal P630 QA", stored!!.name)
        assertEquals("1.2.8", stored.version)
    }

    @Test
    fun eliminarUnProyectoBorraEnCascadaSusHijos() = runBlocking {
        val projectId = insertProject()
        val caseId = insertCase(projectId, "TC-001", TestStatus.PASS)
        val now = System.currentTimeMillis()

        database.testExecutionDao().insert(
            TestExecution(
                projectId = projectId,
                testCaseId = caseId,
                status = TestStatus.PASS,
                executedAt = now
            ).toEntity()
        )
        database.bugDao().insert(
            Bug(
                projectId = projectId,
                testCaseId = caseId,
                code = "BUG-001",
                title = "Defecto",
                severity = Severity.MAJOR,
                priority = Priority.HIGH,
                createdAt = now,
                updatedAt = now
            ).toEntity()
        )
        database.evidenceDao().insert(
            Evidence(
                projectId = projectId,
                testCaseId = caseId,
                displayName = "captura.png",
                uri = "/data/evidence/captura.png",
                type = EvidenceType.SCREENSHOT,
                createdAt = now
            ).toEntity()
        )

        database.projectDao().deleteById(projectId)

        assertNull(database.projectDao().getById(projectId))
        assertTrue(database.testCaseDao().getByProject(projectId).isEmpty())
        assertTrue(database.testExecutionDao().getByProject(projectId).isEmpty())
        assertTrue(database.bugDao().getByProject(projectId).isEmpty())
        assertTrue(database.evidenceDao().getByProject(projectId).isEmpty())
    }

    @Test
    fun losCodigosDeCasoSonUnicosPorProyecto() = runBlocking {
        val projectA = insertProject("Proyecto A")
        val projectB = insertProject("Proyecto B")

        insertCase(projectA, "TC-001", TestStatus.PASS)
        // El mismo código en otro proyecto debe permitirse.
        insertCase(projectB, "TC-001", TestStatus.PASS)

        val duplicated = runCatching { insertCase(projectA, "TC-001", TestStatus.FAIL) }

        assertTrue("El índice único debe rechazar el duplicado", duplicated.isFailure)
    }

    @Test
    fun actualizarElResultadoCambiaElEstadoDelCaso() = runBlocking {
        val projectId = insertProject()
        val caseId = insertCase(projectId, "TC-001", TestStatus.NOT_TESTED)

        database.testCaseDao().updateResult(
            testCaseId = caseId,
            status = TestStatus.FAIL.key,
            severity = Severity.CRITICAL.key,
            actualResult = "La terminal se congela",
            comments = "Reproducible",
            updatedAt = System.currentTimeMillis()
        )

        val stored = database.testCaseDao().getById(caseId)?.toDomain()

        assertNotNull(stored)
        assertEquals(TestStatus.FAIL, stored!!.status)
        assertEquals(Severity.CRITICAL, stored.severity)
        assertEquals("La terminal se congela", stored.actualResult)
    }

    @Test
    fun losContadoresGlobalesReflejanLosEstadosReales() = runBlocking {
        val projectId = insertProject()
        insertCase(projectId, "TC-001", TestStatus.PASS)
        insertCase(projectId, "TC-002", TestStatus.PASS)
        insertCase(projectId, "TC-003", TestStatus.FAIL)
        insertCase(projectId, "TC-004", TestStatus.NOT_TESTED)

        val counts = database.testCaseDao().observeGlobalCounts().first()

        assertNotNull(counts)
        assertEquals(3, counts!!.executed)
        assertEquals(2, counts.passed)
        assertEquals(1, counts.failed)
    }

    @Test
    fun sinCasosLosContadoresGlobalesSonCero() = runBlocking {
        val counts = database.testCaseDao().observeGlobalCounts().first()

        assertNotNull(counts)
        assertEquals(0, counts!!.executed)
        assertEquals(0, counts.passed)
        assertEquals(0, counts.failed)
    }

    @Test
    fun reiniciarResultadosDejaTodosLosCasosSinEjecutar() = runBlocking {
        val projectId = insertProject()
        insertCase(projectId, "TC-001", TestStatus.PASS)
        insertCase(projectId, "TC-002", TestStatus.FAIL)

        database.testCaseDao().resetResults(projectId, System.currentTimeMillis())

        val cases = database.testCaseDao().getByProject(projectId).map { it.toDomain() }

        assertTrue(cases.all { it.status == TestStatus.NOT_TESTED })
        assertTrue(cases.all { it.severity == null })
    }

    @Test
    fun elSiguienteIndiceDeOrdenCreceConCadaCaso() = runBlocking {
        val projectId = insertProject()

        assertEquals(-1, database.testCaseDao().getMaxOrderIndex(projectId))

        insertCase(projectId, "TC-001", TestStatus.PASS)

        assertEquals(0, database.testCaseDao().getMaxOrderIndex(projectId))
    }
}
