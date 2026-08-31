package com.qareport.generator.data.demo

import com.qareport.generator.domain.model.Bug
import com.qareport.generator.domain.model.BugStatus
import com.qareport.generator.domain.model.LogEntry
import com.qareport.generator.domain.model.Priority
import com.qareport.generator.domain.model.Project
import com.qareport.generator.domain.model.Severity
import com.qareport.generator.domain.model.TestCase
import com.qareport.generator.domain.model.TestStatus

/**
 * Datos de demostración.
 *
 * Cubren deliberadamente todos los estados (PASS, FAIL, BLOCKED, SKIPPED, NOT TESTED)
 * y todas las severidades, para poder validar el Dashboard, las gráficas y el PDF
 * sin capturar información a mano.
 */
object DemoDataProvider {

    const val PROJECT_NAME = "Terminal P630 QA"

    fun project(now: Long = System.currentTimeMillis()): Project = Project(
        name = PROJECT_NAME,
        client = "Empresa XYZ",
        product = "Aplicación de pagos POS",
        version = "1.2.8",
        buildNumber = "128",
        deviceType = "Terminal de pago",
        deviceModel = "Verifone P630",
        operatingSystem = "Android",
        androidVersion = "11",
        tester = "Eric",
        environment = "QA",
        startDate = now - DAY_MILLIS * 4,
        endDate = now,
        description = "Pruebas funcionales de la nueva versión 1.2.8.",
        notes = "Datos de demostración generados por la aplicación.",
        finalized = false,
        createdAt = now,
        updatedAt = now
    )

    fun testCases(projectId: Long, now: Long = System.currentTimeMillis()): List<TestCase> = listOf(
        demoCase(
            projectId = projectId,
            index = 0,
            code = "TC-001",
            title = "Instalación del APK",
            module = "Instalación",
            priority = Priority.HIGH,
            preconditions = "Dispositivo restaurado a valores de fábrica.",
            steps = "1. Conectar dispositivo.\n2. Ejecutar instalación.\n3. Abrir aplicación.",
            expected = "La aplicación debe instalarse correctamente.",
            actual = "Instalación completada sin errores.",
            status = TestStatus.PASS,
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 1,
            code = "TC-002",
            title = "Inicio de aplicación",
            module = "Arranque",
            priority = Priority.HIGH,
            preconditions = "Aplicación instalada.",
            steps = "1. Abrir la aplicación.\n2. Esperar la pantalla principal.",
            expected = "La pantalla principal debe cargar en menos de 5 segundos.",
            actual = "Carga en 2 segundos.",
            status = TestStatus.PASS,
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 2,
            code = "TC-003",
            title = "Login de operador",
            module = "Seguridad",
            priority = Priority.CRITICAL,
            preconditions = "Operador dado de alta.",
            steps = "1. Ingresar usuario.\n2. Ingresar contraseña.\n3. Confirmar.",
            expected = "El operador debe autenticarse correctamente.",
            actual = "Autenticación correcta.",
            status = TestStatus.PASS,
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 3,
            code = "TC-004",
            title = "Transacción con chip",
            module = "Transacciones",
            priority = Priority.CRITICAL,
            preconditions = "Tarjeta de prueba con chip disponible.",
            steps = "1. Iniciar venta.\n2. Insertar tarjeta.\n3. Capturar monto.\n4. Confirmar.",
            expected = "La transacción debe aprobarse.",
            actual = "Transacción aprobada con código 00.",
            status = TestStatus.PASS,
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 4,
            code = "TC-005",
            title = "Transacción con banda (swipe)",
            module = "Transacciones",
            priority = Priority.MEDIUM,
            preconditions = "Tarjeta de prueba con banda magnética.",
            steps = "1. Iniciar venta.\n2. Deslizar tarjeta.\n3. Confirmar monto.",
            expected = "La transacción debe aprobarse.",
            actual = "Transacción aprobada.",
            status = TestStatus.PASS,
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 5,
            code = "TC-006",
            title = "Transacción contactless (CTLS)",
            module = "Transacciones",
            priority = Priority.CRITICAL,
            preconditions = "Tarjeta contactless disponible.",
            steps = "1. Iniciar venta.\n2. Seleccionar CTLS.\n3. Acercar tarjeta.\n4. Esperar respuesta.",
            expected = "La transacción debe aprobarse.",
            actual = "La terminal queda congelada y no responde.",
            status = TestStatus.FAIL,
            severity = Severity.CRITICAL,
            comments = "Reproducible 3 de 3 intentos.",
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 6,
            code = "TC-007",
            title = "Impresión de comprobante",
            module = "Impresión",
            priority = Priority.HIGH,
            preconditions = "Rollo de papel instalado.",
            steps = "1. Finalizar una venta.\n2. Confirmar impresión.",
            expected = "El comprobante debe imprimirse completo y legible.",
            actual = "No fue posible probar: la impresora del equipo de pruebas está averiada.",
            status = TestStatus.BLOCKED,
            severity = Severity.MAJOR,
            comments = "Bloqueado por hardware, no por la aplicación.",
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 7,
            code = "TC-008",
            title = "Cancelación de transacción",
            module = "Transacciones",
            priority = Priority.HIGH,
            preconditions = "Transacción aprobada previamente.",
            steps = "1. Entrar al menú de cancelación.\n2. Seleccionar la transacción.\n3. Confirmar.",
            expected = "La transacción debe cancelarse y notificarse al host.",
            actual = "La cancelación se procesa pero el comprobante omite el número de autorización.",
            status = TestStatus.FAIL,
            severity = Severity.MINOR,
            comments = "Solo afecta la impresión, no el saldo.",
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 8,
            code = "TC-009",
            title = "Reverso automático",
            module = "Transacciones",
            priority = Priority.MEDIUM,
            preconditions = "Simulador de caída de comunicación disponible.",
            steps = "1. Iniciar venta.\n2. Cortar comunicación durante la autorización.",
            expected = "La terminal debe enviar el reverso al restablecer la comunicación.",
            actual = "",
            status = TestStatus.SKIPPED,
            comments = "Se omite: el simulador no está disponible en este ciclo.",
            now = now
        ),
        demoCase(
            projectId = projectId,
            index = 9,
            code = "TC-010",
            title = "Cierre de aplicación",
            module = "Arranque",
            priority = Priority.LOW,
            preconditions = "Aplicación abierta.",
            steps = "1. Salir de la aplicación desde el menú.",
            expected = "La aplicación debe cerrarse liberando la sesión del operador.",
            actual = "",
            status = TestStatus.NOT_TESTED,
            now = now
        )
    )

    fun bugs(projectId: Long, contactlessCaseId: Long?, cancelCaseId: Long?, now: Long): List<Bug> =
        listOf(
            Bug(
                projectId = projectId,
                testCaseId = contactlessCaseId,
                code = "BUG-001",
                title = "Terminal congelada durante CTLS",
                description = "La terminal deja de responder durante la transacción contactless.",
                severity = Severity.CRITICAL,
                priority = Priority.HIGH,
                stepsToReproduce = "1. Iniciar transacción.\n2. Seleccionar CTLS.\n" +
                    "3. Acercar tarjeta.\n4. Esperar respuesta.",
                expectedResult = "La transacción debe continuar.",
                actualResult = "La terminal queda congelada y requiere reinicio.",
                version = "1.2.8",
                device = "Verifone P630",
                status = BugStatus.OPEN,
                createdAt = now - DAY_MILLIS,
                updatedAt = now - DAY_MILLIS
            ),
            Bug(
                projectId = projectId,
                testCaseId = cancelCaseId,
                code = "BUG-002",
                title = "Comprobante de cancelación sin número de autorización",
                description = "El comprobante impreso tras una cancelación no incluye el número " +
                    "de autorización de la transacción original.",
                severity = Severity.MINOR,
                priority = Priority.MEDIUM,
                stepsToReproduce = "1. Cancelar una transacción aprobada.\n2. Revisar el comprobante.",
                expectedResult = "El comprobante debe incluir el número de autorización.",
                actualResult = "El campo aparece vacío.",
                version = "1.2.8",
                device = "Verifone P630",
                status = BugStatus.IN_PROGRESS,
                createdAt = now - DAY_MILLIS / 2,
                updatedAt = now
            ),
            Bug(
                projectId = projectId,
                testCaseId = null,
                code = "BUG-003",
                title = "Retardo al abrir el menú de configuración",
                description = "El menú de configuración tarda más de 4 segundos en abrir.",
                severity = Severity.MAJOR,
                priority = Priority.LOW,
                stepsToReproduce = "1. Abrir la aplicación.\n2. Entrar a Configuración.",
                expectedResult = "El menú debe abrir en menos de 1 segundo.",
                actualResult = "Tarda entre 4 y 6 segundos.",
                version = "1.2.8",
                device = "Verifone P630",
                status = BugStatus.CLOSED,
                createdAt = now - DAY_MILLIS * 2,
                updatedAt = now
            )
        )

    fun logs(projectId: Long, now: Long): List<LogEntry> = listOf(
        LogEntry(
            projectId = projectId,
            title = "Logcat - transacción CTLS",
            content = """
                08:42:12 INFO  Start transaction
                08:42:13 INFO  Amount captured: 150.00
                08:42:15 INFO  Card detected (CTLS)
                08:42:16 ERROR Transaction failed: NFC_TIMEOUT
                08:42:16 ERROR Watchdog: UI thread blocked 5200 ms
            """.trimIndent(),
            createdAt = now - DAY_MILLIS
        ),
        LogEntry(
            projectId = projectId,
            title = "Logcat - arranque",
            content = """
                08:30:01 INFO  Application start
                08:30:02 INFO  Database ready
                08:30:03 INFO  Main screen displayed in 1980 ms
            """.trimIndent(),
            createdAt = now - DAY_MILLIS * 2
        )
    )

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    @Suppress("LongParameterList")
    private fun demoCase(
        projectId: Long,
        index: Int,
        code: String,
        title: String,
        module: String,
        priority: Priority,
        preconditions: String,
        steps: String,
        expected: String,
        actual: String,
        status: TestStatus,
        now: Long,
        severity: Severity? = null,
        comments: String = ""
    ): TestCase = TestCase(
        projectId = projectId,
        code = code,
        title = title,
        module = module,
        priority = priority,
        preconditions = preconditions,
        steps = steps,
        expectedResult = expected,
        actualResult = actual,
        status = status,
        severity = severity,
        comments = comments,
        orderIndex = index,
        createdAt = now,
        updatedAt = now
    )
}
