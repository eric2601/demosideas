# Arquitectura y análisis de compatibilidad

Documento de diseño de **QA Report Generator**. Recoge el análisis previo a escribir el
código y las decisiones técnicas tomadas, con el criterio de prioridad pedido:

> **estabilidad > funcionalidad > diseño**

---

## 1. Análisis de compatibilidad entre librerías

El riesgo principal de un proyecto Android moderno no es escribir el código, sino elegir
un conjunto de versiones que encaje entre sí. Estas son las restricciones que se
verificaron **antes** de escribir nada:

| Restricción | Consecuencia |
|---|---|
| Kotlin **2.0.x** movió el compilador de Compose fuera de `composeOptions` | Es **obligatorio** aplicar el plugin `org.jetbrains.kotlin.plugin.compose` con la **misma versión que Kotlin**. Sin él, el build falla con *"This version of the Compose Compiler requires Kotlin version…"*. |
| KSP publica una versión por cada versión de Kotlin | `ksp = "2.0.21-1.0.25"` — el prefijo debe coincidir **exactamente** con `kotlin = "2.0.21"`. Un desajuste rompe el procesador de Room. |
| AGP **8.5.2** exige Gradle **8.7+** y JDK **17+** | Wrapper fijado en **8.9** y `jvmTarget = 17`. |
| `androidx.lifecycle` **2.8.x** depende de `compose-runtime` 1.7 | Se usa el **Compose BOM 2024.09.03** (Compose 1.7.x, Material 3 1.3.x), que satisface esa dependencia sin conflictos de versión. |
| Room **2.6.1** con KSP requiere `room.schemaLocation` | Configurado en `ksp { arg(...) }` para poder escribir migraciones reales. |
| `java.time` no existe por debajo de API 26 | Se descartó `java.time` y el *core library desugaring* asociado; todo el formateo usa `SimpleDateFormat`/`Calendar`, disponibles desde API 1 y también en la JVM (lo que permite probarlos con tests unitarios normales). Esto elimina una dependencia y un modo de fallo. |
| `file://` lanza `FileUriExposedException` desde API 24 | Todo archivo que sale de la app pasa por `FileProvider`. |

### Versiones finales

```
AGP 8.5.2 · Gradle 8.9 · Kotlin 2.0.21 · KSP 2.0.21-1.0.25
Compose BOM 2024.09.03 · Material 3 · Room 2.6.1 · Coroutines 1.8.1
compileSdk 34 · targetSdk 34 · minSdk 24 · JDK 17
```

`minSdk 24` cubre prácticamente todo el parque de terminales Android en uso y evita el
desugaring y las rutas de compatibilidad de API 21-23.

---

## 2. Dependencias descartadas a propósito

El enunciado pide evitar dependencias innecesarias. Cada una de estas se sustituyó por
código propio y verificado:

| Descartada | Sustituida por | Motivo |
|---|---|---|
| iText / PdfBox | `android.graphics.pdf.PdfDocument` | Está en la plataforma; el PDF se genera local, sin licencias ni peso extra. |
| MPAndroidChart | `Canvas` de Compose (`DonutChart`, `HorizontalBarChart`, `LineChart`) | Sólo hacen falta tres gráficas sencillas. |
| kotlinx.serialization | `Json` / `JsonParser` propios | Evita un plugin de compilación adicional; además `org.json` está *stubbeado* en los tests unitarios JVM y devolvería valores por defecto. El JSON propio es Kotlin puro y **totalmente testeable**. |
| Hilt / Dagger | `AppContainer` manual | El grafo es pequeño y estático; se evita un segundo procesador de anotaciones. |
| DataStore | `SharedPreferences` + `callbackFlow` | Siete preferencias booleanas no justifican otra dependencia. |
| Glide / Coil | `BitmapFactory` con `inSampleSize` | Sólo se decodifican imágenes locales para incrustarlas en el PDF. |

`viewBinding` **sí** está habilitado, tal como pide el enunciado, para poder interoperar
con layouts XML sin `findViewById`. La UI principal, no obstante, es Jetpack Compose:
es lo coherente con `LazyColumn`, Material 3 y la nomenclatura `*Screen.kt` solicitada.

---

## 3. Capas

```
┌──────────────────────────────────────────────┐
│ UI · Jetpack Compose + Navigation            │
│   screens/ components/ theme/ navigation/    │
└───────────────────┬──────────────────────────┘
                    │ StateFlow / callbacks
┌───────────────────▼──────────────────────────┐
│ ViewModel · MVVM                             │
│   estado de UI, validación, mensajes         │
└───────────────────┬──────────────────────────┘
                    │ Outcome<T> (nunca excepciones)
┌───────────────────▼──────────────────────────┐
│ Repository · Dispatchers.IO                  │
│   mapea entidades ⇄ modelos de dominio       │
└──────┬────────────────────────┬──────────────┘
       │                        │
┌──────▼────────┐      ┌────────▼──────────────┐
│ Room          │      │ Sistema de archivos   │
│ 7 entidades   │      │ reports/ exports/     │
│ 7 DAOs        │      │ evidence/ (privado)   │
└───────────────┘      └───────────────────────┘

        ┌──────────────────────────────────┐
        │ Domain · Kotlin puro, sin Android│
        │  estadísticas, conclusión,       │
        │  validaciones, exportadores      │
        └──────────────────────────────────┘
```

La capa `domain` no importa nada de Android. Es una decisión de **testabilidad**: toda
la lógica que decide qué dice el reporte se ejercita con tests unitarios JVM rápidos,
sin emulador.

---

## 4. Estabilidad: cómo se evita que la app se cierre

El requisito «la aplicación nunca debe cerrarse por un `NullPointerException`» se aborda
de forma estructural, no a base de `try/catch` dispersos:

1. **Enums tolerantes.** Todo enum persistido se lee con un `from(value: String?)` que
   devuelve un valor por defecto ante `null`, texto desconocido o dato corrupto. Un
   registro dañado degrada la vista; no la tumba.
2. **`Outcome<T>` en lugar de excepciones.** Los repositorios devuelven
   `Outcome.Success` / `Outcome.Failure`. El ViewModel traduce el fallo a un mensaje de
   Snackbar. `safeCall` centraliza el `try/catch` y **relanza `CancellationException`**,
   porque cancelar una corrutina no es un error.
3. **Sin nulos en los modelos.** Los modelos de dominio tienen valores por defecto para
   todos los campos de texto; sólo son nulables los que representan ausencia real
   (`severity`, fechas, `testCaseId`).
4. **Aritmética defendida.** `passRate` y `executionRate` devuelven `0.0` cuando no hay
   casos: no hay ninguna división por cero posible.
5. **E/S siempre protegida.** Lectura de URIs, copia de evidencias, escritura del PDF y
   decodificación de imágenes capturan `IOException`, `SecurityException` y
   `OutOfMemoryError`, registran en Logcat y devuelven `null` o un `Outcome.Failure`.
6. **Memoria acotada.** Las imágenes se decodifican con `inSampleSize` y `RGB_565`,
   se reciclan tras dibujarlas, las evidencias se limitan a 25 MB y los logs se truncan
   en el PDF (el TXT exportado los conserva completos).
7. **Sin `fallbackToDestructiveMigration`.** Una actualización nunca borrará los datos
   del tester en silencio; los esquemas se exportan para escribir migraciones reales.

---

## 5. Modelo de datos

| Entidad | Clave foránea | `onDelete` |
|---|---|---|
| `projects` | — | — |
| `test_cases` | `project_id` | CASCADE |
| `test_executions` | `project_id`, `test_case_id` | CASCADE |
| `bugs` | `project_id` | CASCADE |
| `bugs` | `test_case_id` | SET NULL |
| `evidences` | `project_id`, `test_case_id`, `bug_id` | CASCADE |
| `log_entries` | `project_id` | CASCADE |
| `log_entries` | `test_case_id` | SET NULL |
| `reports` | `project_id` | CASCADE |

Índice **único** en `(project_id, code)` para casos y defectos: garantiza a nivel de base
de datos que no haya dos `TC-001` en el mismo proyecto, además de la validación en UI.

Un defecto sobrevive al borrado de su caso de prueba (`SET NULL`) porque el defecto es
información de negocio con valor propio; una ejecución, en cambio, no tiene sentido sin
su caso (`CASCADE`).

---

## 6. Ejecución y registro de resultados

`TestExecutionViewModel` mantiene un índice sobre la lista de casos y arranca en el
primer caso pendiente. Al registrar un resultado:

* Se inserta una fila en `test_executions` (histórico completo, nunca se sobrescribe).
* Se actualiza el estado actual en `test_cases`, que es lo que leen el Dashboard y el PDF.

`FAIL` exige los cinco campos de diagnóstico antes de poder guardarse y, opcionalmente,
crea el defecto asociado **reutilizando exactamente esos datos**, sin generar
información nueva.

---

## 7. Generación del PDF

`PdfPageWriter` encapsula la paginación: mantiene la posición vertical, abre página
nueva cuando el contenido no cabe y pinta el pie con el número de página. El generador
sólo describe contenido.

El ajuste de línea usa `StaticLayout` para calcular los puntos de corte y luego dibuja
línea a línea con `Canvas.drawText`; así un párrafo largo puede repartirse entre páginas,
algo que no permite dibujar un `StaticLayout` completo de una vez.

Página A4 en puntos PostScript (595 × 842), que es la unidad nativa de `PdfDocument`.

---

## 8. Conclusión automática

`ConclusionGenerator` es una función pura de `ProjectStats` → texto. Reglas:

| Condición (evaluada en orden) | Recomendación |
|---|---|
| Sin casos registrados | Sin datos suficientes (no emite juicio) |
| Quedan casos `NOT TESTED` | Ejecución incompleta |
| Hay defectos `BLOCKER` o `CRITICAL` | Requiere correcciones |
| Hay fallos y *pass rate* < 80 % | Requiere correcciones |
| Hay fallos, bloqueos o *pass rate* < 95 % | Apta con reservas |
| Resto | Apta para liberación |

Los umbrales son constantes públicas y documentadas. El texto sólo menciona severidades
que existen realmente y usa singular o plural según el conteo real.

---

## 9. Estrategia de pruebas

**Unitarias JVM (`app/src/test`) — 58 pruebas**

* `StatsCalculatorTest` — conteos, *pass rate*, agrupación por módulo, códigos correlativos.
* `ConclusionGeneratorTest` — cada recomendación y que el texto reproduzca los números reales.
* `ValidatorsTest` — campos obligatorios, fechas, duplicados, reglas del reporte.
* `ExportersTest` — round-trip del respaldo JSON, escapado CSV (RFC 4180) y secciones del TXT.
* `EnumsAndUtilsTest` — tolerancia a datos corruptos, nombres de archivo, fechas.

**Instrumentadas (`app/src/androidTest`)**

* `AppDatabaseTest` — esquema real, borrado en cascada, índice único, contadores globales.
* `PdfReportGeneratorTest` — genera PDFs reales (incluido uno de 120 casos multipágina)
  y comprueba que el archivo empieza por la cabecera `%PDF`.

---

## 10. Navegación

```
MainActivity (única Activity, todo Compose)
   ├── Dashboard
   ├── Projects ── ProjectDetail ── TestCases ── TestCaseEditor
   │                              ├── ExecuteTests
   │                              ├── Bugs ── BugEditor
   │                              ├── Evidence
   │                              └── Logs
   ├── Tests
   ├── Reports
   └── Settings
```

Se usan rutas con argumentos en la cadena (`project_detail/{projectId}`) en lugar de las
rutas tipadas de Navigation 2.8, que exigirían `kotlinx-serialization`.
