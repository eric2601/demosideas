# QA Report Generator

Aplicación **Android nativa** (Kotlin + Jetpack Compose) para que un tester cree y
administre proyectos de pruebas de calidad y genere un **reporte QA profesional en PDF**
directamente desde el dispositivo.

La aplicación funciona **100 % offline**: no declara el permiso `INTERNET` y ningún dato
sale del dispositivo salvo que el usuario comparta un archivo explícitamente.

---

## 1. Flujo de la aplicación

```
Crear proyecto QA → Registrar información → Crear casos de prueba → Ejecutar pruebas
→ Registrar PASS / FAIL / BLOCKED / SKIPPED → Observaciones → Evidencias
→ Finalizar pruebas → Generar reporte → Compartir / Exportar
```

---

## 2. Compilar el APK

### Requisitos

| Herramienta | Versión |
|---|---|
| Android Studio | Ladybug (2024.2) o superior |
| JDK | 17 (incluido en Android Studio) |
| Android SDK Platform | 34 |
| Gradle | 8.9 (lo descarga el wrapper) |

### Pasos

1. Abre la carpeta del proyecto en Android Studio (`File → Open`).
2. Espera a que termine el *Gradle Sync*. La primera vez descargará AGP, Kotlin,
   Compose y Room desde Google Maven y Maven Central.
3. Genera el APK de depuración:

```bash
./gradlew assembleDebug
```

El APK queda en:

```
app/build/outputs/apk/debug/app-debug.apk
```

Instalación directa en un dispositivo conectado:

```bash
./gradlew installDebug
```

### Build de release

```bash
./gradlew assembleRelease
```

El build de release aplica **R8** (`minifyEnabled` + `shrinkResources`).

La firma es **opcional y no está en el control de versiones**. Para firmar, crea un
archivo `keystore.properties` en la raíz del proyecto (ya está en `.gitignore`):

```properties
storeFile=/ruta/absoluta/a/tu/keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Si el archivo no existe, `assembleRelease` genera igualmente el APK, sin firmar.
**Nunca** se incluyen claves, contraseñas ni keystores en el repositorio.

### Pruebas

```bash
./gradlew testDebugUnitTest          # 58 pruebas unitarias JVM (dominio)
./gradlew connectedDebugAndroidTest  # Room + generación real de PDF (requiere dispositivo)
```

---

## 3. Arquitectura

MVVM con capas separadas y dependencias en una sola dirección:

```
UI (Compose)  →  ViewModel  →  Repository  →  Room / Sistema de archivos
                                    ↓
                                 Domain (Kotlin puro)
```

* **`domain/`** — modelos, enums, cálculo de estadísticas, generación de la conclusión,
  validaciones y exportadores. **No depende de Android**, por eso está cubierto con
  pruebas unitarias JVM normales.
* **`data/`** — entidades Room, DAOs, mappers, repositorios, preferencias y datos demo.
* **`report/`** — generación del PDF con `android.graphics.pdf` y orquestación
  (`ReportService`).
* **`ui/`** — tema Material 3, componentes, gráficas, pantallas y navegación.
* **`utils/`** — almacenamiento de archivos, compartición, carga de imágenes y logging.
* **`di/`** — contenedor de dependencias manual (`AppContainer`).

Ver el detalle completo, incluido el análisis de compatibilidad de librerías, en
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

### Estructura de carpetas

```
app/src/main/java/com/qareport/generator/
├── QaApplication.kt
├── MainActivity.kt
├── core/           Outcome, safeCall
├── di/             AppContainer, factories de ViewModel
├── domain/
│   ├── model/      Enums, modelos, ProjectStats
│   ├── stats/      StatsCalculator
│   ├── report/     ConclusionGenerator
│   ├── validation/ Validators, ValidationResult
│   ├── export/     Json, JsonParser, ProjectJsonExporter, CsvExporter, TxtExporter
│   └── util/       DateFormats, TextUtils
├── data/
│   ├── local/      entity/, dao/, AppDatabase
│   ├── mapper/     Mappers
│   ├── repository/ Project, TestCase, Execution, Bug, Evidence, Log, Report
│   ├── prefs/      SettingsRepository
│   └── demo/       DemoDataProvider, DemoDataLoader
├── report/         PdfPageWriter, PdfReportGenerator, ReportService
├── ui/
│   ├── theme/      Color, Type, Theme
│   ├── common/     BaseViewModel, UiMessage, helpers de formulario
│   ├── components/ StatCard, SectionCard, chips, gráficas
│   ├── viewmodel/  12 ViewModels
│   ├── screens/    13 pantallas
│   └── navigation/ Routes, QaNavHost
└── utils/          AppLogger, FileStorage, ShareUtils, BitmapLoader
```

---

## 4. Base de datos

Room, versión 1, esquemas exportados a `app/schemas/`.

```
Project
   ├── TestCase        (CASCADE)
   │      ├── TestExecution (CASCADE)
   │      └── Evidence      (CASCADE)
   ├── Bug             (CASCADE, TestCase → SET NULL)
   ├── LogEntry        (CASCADE)
   ├── Evidence        (CASCADE)
   └── Report          (CASCADE)
```

Las evidencias **no guardan binarios en Room**: se copian al almacenamiento privado de
la aplicación y en la base de datos sólo queda la ruta y los metadatos.

---

## 5. Reporte PDF

Se genera localmente con `android.graphics.pdf.PdfDocument`, **sin librerías externas**.
Contiene:

1. **Portada** — proyecto, cliente, versión, build, dispositivo, ambiente, tester y fecha.
2. **Resumen ejecutivo** — totales por estado, barra de distribución y *pass rate*.
3. **Información del ambiente**.
4. **Matriz de pruebas** — tabla ID / caso / resultado / severidad.
5. **Detalle de pruebas fallidas** — con pasos y evidencias embebidas.
6. **Defectos registrados**.
7. **Evidencias**.
8. **Logs**.
9. **Conclusión automática**.

La conclusión se **calcula exclusivamente a partir de los datos registrados**
(`ConclusionGenerator`): si no hay casos no emite juicio, si quedan casos sin ejecutar
marca la cobertura como incompleta y la recomendación de liberación se deriva de
umbrales fijos y documentados. **No inventa ningún dato.**

---

## 6. Validaciones

No se permite generar un reporte vacío. Los mensajes son explícitos:

* `"El nombre del proyecto es obligatorio."`
* `"Debes agregar al menos un caso de prueba."`
* `"Debes finalizar las pruebas antes de generar el reporte."`

Al marcar un caso como **FAIL** son obligatorios: descripción del problema, severidad,
pasos para reproducir, resultado esperado y resultado actual.

---

## 7. Exportación y compartición

| Formato | Contenido |
|---|---|
| PDF | Reporte QA completo |
| CSV | Matriz de casos (y de defectos por separado), con BOM UTF-8 para Excel |
| JSON | Respaldo completo del proyecto, con round-trip verificado por pruebas |
| TXT | Reporte en texto plano |

Compartir usa el **Android Sharesheet** (`Intent.ACTION_SEND`) con `FileProvider`.
No se asume que ninguna aplicación concreta esté instalada.

---

## 8. Datos de demostración

Desde el Dashboard, *Cargar datos de demostración* crea el proyecto
**Terminal P630 QA** con 10 casos (TC-001 a TC-010) que cubren todos los estados
(PASS, FAIL, BLOCKED, SKIPPED, NOT TESTED), 3 defectos de distinta severidad y 2 logs,
para poder validar el Dashboard, las gráficas y el PDF sin capturar nada a mano.

---

## 9. Seguridad y privacidad

* Sin permiso `INTERNET`; nada se envía a servidores externos.
* Sin API keys, contraseñas, tokens ni credenciales en el código.
* Reportes, exportaciones y evidencias viven en el almacenamiento privado de la app,
  por lo que no requieren permisos de almacenamiento.
* El permiso `CAMERA` se solicita en el momento de tomar una foto, no al arrancar.
* Los logs técnicos registran identificadores y contadores, nunca contenido de
  evidencias ni datos personales.
