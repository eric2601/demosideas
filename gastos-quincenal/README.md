# Control de Gastos Quincenal

Aplicacion Android **nativa** (Kotlin + Jetpack Compose + Room) para administrar
gastos personales divididos en dos quincenas por mes.

No es una pagina web, no usa WebView y no es hibrida: es un proyecto de Android
Studio que compila y genera un APK.

---

## 1. Concepto

Cada mes se divide automaticamente en dos periodos:

| Quincena | Dias | Color |
|---|---|---|
| Primera | 1 al 15 | Azul cielo |
| Segunda | 16 al ultimo dia real del mes | Naranja |

La segunda quincena termina el 28, 29, 30 o 31 segun el mes y el ano: los
calculos usan `LocalDate` y `YearMonth`, nunca se asume que un mes tiene 30 dias.

La quincena de un movimiento **no se elige a mano**: se deduce de la fecha
(`Quincena.of(date)`).

---

## 2. Requisitos

- Android Studio Ladybug (2024.2.1) o posterior
- JDK 17 (el incluido en Android Studio sirve)
- Android SDK Platform 34
- Dispositivo o emulador con Android 7.0 (API 24) o superior

---

## 3. Generar el APK

```bash
cd gastos-quincenal
./gradlew assembleDebug
```

El archivo queda en:

```
app/build/outputs/apk/debug/app-debug.apk
```

Instalarlo en un dispositivo conectado:

```bash
./gradlew installDebug
```

Otras tareas utiles:

```bash
./gradlew test                  # pruebas unitarias (JVM)
./gradlew connectedAndroidTest  # pruebas instrumentadas (requiere emulador)
./gradlew lint                  # analisis estatico
./gradlew assembleRelease       # APK de release (sin firmar)
```

Desde Android Studio: **File > Open**, elegir la carpeta `gastos-quincenal`,
esperar la sincronizacion de Gradle y usar **Build > Build Bundle(s)/APK(s) >
Build APK(s)**.

---

## 4. Dependencias y compatibilidad

El conjunto de versiones esta fijado en `gradle/libs.versions.toml` y fue elegido
para ser mutuamente compatible:

| Componente | Version | Por que esa version |
|---|---|---|
| Gradle | 8.9 | AGP 8.5.x exige Gradle 8.7 o superior |
| Android Gradle Plugin | 8.5.2 | Estable; requiere JDK 17 |
| Kotlin | 2.0.21 | Desde Kotlin 2.0 el compilador de Compose viaja en el plugin `kotlin-compose` con la misma version |
| KSP | 2.0.21-1.0.25 | El prefijo debe coincidir **exactamente** con la version de Kotlin |
| Compose BOM | 2024.09.03 | Alinea todas las librerias `androidx.compose.*` entre si |
| Material 3 | via BOM | Evita mezclar versiones incompatibles |
| Room | 2.6.1 | Ultima estable con soporte KSP en esta combinacion |
| Navigation Compose | 2.8.2 | Compatible con Compose BOM 2024.09 |
| Lifecycle | 2.8.6 | Aporta `collectAsStateWithLifecycle` |
| Coroutines | 1.8.1 | Compatible con Kotlin 2.0.x |
| desugar_jdk_libs | 2.0.4 | **Necesario**: permite usar `java.time` con `minSdk 24` |

`compileSdk = 34`, `targetSdk = 34`, `minSdk = 24`.

> Sin *core library desugaring* la aplicacion no compilaria con `minSdk 24`
> porque `LocalDate` y `YearMonth` solo existen de forma nativa desde API 26.

---

## 5. Arquitectura

```
UI (Compose)
   ↓
ViewModel  (estado de pantalla, sin logica financiera)
   ↓
Use Cases  (composicion y validacion)
   ↓
Repository (fuente unica de verdad)
   ↓
Room       (SQLite local)
```

Ningun calculo financiero vive dentro de un `Composable` ni de un `ViewModel`:
todos estan en `FinancialCalculator` y `RecurrenceEngine`, que son clases puras
de Kotlin sin dependencias de Android y por eso estan cubiertas por pruebas
unitarias de JVM.

La inyeccion de dependencias es manual (`AppContainer`): el grafo es pequeno y
asi el build no depende de otro procesador de anotaciones.

### Estructura de carpetas

```
app/src/main/java/com/gastos/quincenal/
├── GastosApplication.kt
├── MainActivity.kt
├── di/
│   ├── AppContainer.kt
│   └── ViewModelFactories.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── Converters.kt
│   │   ├── DefaultCategories.kt
│   │   ├── entity/Entities.kt
│   │   └── dao/{CategoryDao, RecurrenceDao, ExpenseDao, IncomeDao, SettingsDao}.kt
│   ├── mapper/Mappers.kt
│   ├── repository/
│   │   ├── BudgetRepository.kt
│   │   ├── OccurrenceExpander.kt
│   │   └── SettingsRepository.kt
│   ├── backup/{BackupModels, BackupSerializer, BackupRepository}.kt
│   └── demo/DemoDataLoader.kt
├── domain/
│   ├── model/{Money, Quincena, Enums, Category, Occurrence, Summaries}.kt
│   ├── recurrence/{RecurrenceRule, RecurrenceEngine}.kt
│   ├── calc/{FinancialCalculator, WeekCalculator}.kt
│   └── usecase/{GetMonthOverview, SaveExpense, SaveIncome, DuplicateMonth, GetHistory}.kt
├── ui/
│   ├── theme/{Color, QuincenaColors, Type, Theme}.kt
│   ├── components/  (calendario, tarjetas de quincena, graficas, formularios)
│   ├── navigation/{Routes, GastosNavHost}.kt
│   ├── common/{MonthAwareViewModel, UiMessage}.kt
│   ├── dashboard/  calendar/  expenses/  income/
│   └── quincena/   statistics/  history/  settings/
└── utils/{DateUtils, CurrencyFormatter}.kt
```

---

## 6. Sistema de recurrencia

Este es el punto de diseno mas importante de la aplicacion.

**La aplicacion no duplica filas mes a mes.** Guarda una *regla* y calcula las
fechas del mes que se esta consultando:

- `ExpenseEntity` / `IncomeEntity` guardan la definicion del movimiento.
- `RecurrenceEntity` guarda la regla (frecuencia, intervalo, inicio, fin, dia del
  mes o dia de la semana).
- `RecurrenceEngine` expande la regla a las fechas concretas de un mes.
- `ExpenseOverrideEntity` / `IncomeOverrideEntity` guardan las excepciones de una
  ocurrencia concreta: marcarla pagada, cambiarle el importe, moverla de fecha o
  saltarla, **sin afectar a los demas meses**.

Frecuencias soportadas: una sola vez, cada semana, cada 2 semanas, cada mes,
cada ano, y cualquiera de ellas con intervalo personalizado (cada 3 meses, etc.).

Fin de la recurrencia: solo este mes, indefinidamente, o hasta una fecha.

Casos cubiertos por pruebas:

- Un gasto "solo este mes" **no** reaparece en el mes siguiente.
- Un gasto "cada mes" aparece en todos los meses posteriores con **una sola fila**
  en la base de datos.
- El dia 31 se recorta al 28, 29 o 30 segun el mes.
- "Cada 2 semanas" mantiene el ritmo al cruzar de mes.
- La fecha limite corta la serie.
- Marcar pagada la ocurrencia de febrero no marca la de enero ni la de marzo.

---

## 7. Calculos financieros

Todo el dinero se representa con `Money`, un `value class` sobre **centavos
enteros**. No se usa `Double` para acumular importes porque el punto flotante
introduce errores (`0.1 + 0.2 != 0.3`) y el requisito principal del proyecto es
la exactitud.

`FinancialCalculator` calcula: total de ingresos, total de gastos, gastos e
ingresos de cada quincena, sobrante quincenal, sobrante mensual, resumen semanal,
gasto por categoria, desglose diario y las alertas de presupuesto.

Las semanas se calculan de domingo a sabado recortadas al mes, de modo que
"Semana 2" significa lo mismo en el resumen y en el calendario.

### Nota sobre los datos de ejemplo

La hoja de referencia anota `TOTAL GASTOS 12,434` en la primera quincena y
`8,954` en la segunda, pero **la suma de los conceptos que enumera da 12,014 y
8,554** (faltan 420 y 400 respectivamente, probablemente por renglones que no se
transcribieron).

Los datos de ejemplo respetan los importes proporcionados tal cual, y la
aplicacion muestra la suma real de los conceptos registrados. Cuadrar con un
total anotado a mano inventando cantidades seria justo el tipo de error que este
proyecto busca evitar.

---

## 8. Base de datos

Entidades: `CategoryEntity`, `RecurrenceEntity`, `ExpenseEntity`,
`ExpenseOverrideEntity`, `IncomeEntity`, `IncomeOverrideEntity`, `MonthEntity`,
`UserSettingsEntity`.

Detalles relevantes:

- Las fechas se guardan como **dia epoch** (entero): ordena y compara
  directamente en SQL y no depende de la zona horaria del dispositivo.
- Los enums se guardan **por nombre**, para que el respaldo sea legible y agregar
  valores nuevos no rompa los datos existentes.
- Las operaciones de guardado usan `@Upsert` y **no** `@Insert(REPLACE)`: REPLACE
  borra la fila antes de reinsertarla y eso dispararia el `ON DELETE CASCADE` de
  las excepciones por ocurrencia, perdiendo los pagos marcados.
- El esquema se exporta a `app/schemas` para poder escribir migraciones.

---

## 9. Respaldo

`EXPORTAR RESPALDO` genera un JSON con ingresos, gastos, categorias,
recurrencias, excepciones, notas por mes y configuracion.
`IMPORTAR RESPALDO` lo restaura dentro de una transaccion: si algo falla, la base
queda intacta.

Se usa el **Storage Access Framework**: el usuario elige el archivo con el
selector del sistema, por lo que la aplicacion no necesita permisos de
almacenamiento.

---

## 10. Privacidad y funcionamiento sin conexion

- El `AndroidManifest.xml` **no declara el permiso `INTERNET`**.
- No se declara ningun otro permiso.
- Toda la informacion vive en la base de datos local del dispositivo.
- Crear, editar y borrar movimientos, consultar el calendario, calcular
  quincenas, ver estadisticas y exportar el respaldo funcionan sin conexion.

---

## 11. Pruebas

Pruebas unitarias (`app/src/test`, se ejecutan en la JVM):

| Clase | Que verifica |
|---|---|
| `QuincenaTest` | Division 1-15 / 16-fin, febrero de 28 y 29 dias, meses de 30 |
| `MoneyTest` | Exactitud de las sumas, lectura de cantidades escritas por el usuario |
| `RecurrenceEngineTest` | Mensual, semanal, quincenal, anual, intervalos, fecha limite |
| `RecurrenceRuleBuilderTest` | Traduccion del formulario a regla de recurrencia |
| `WeekCalculatorTest` | Semanas de domingo a sabado sin huecos ni solapes |
| `FinancialCalculatorTest` | Totales, sobrantes, categorias, alertas |

Pruebas instrumentadas (`app/src/androidTest`, requieren emulador):

| Clase | Que verifica |
|---|---|
| `BudgetRepositoryTest` | Expansion de recurrencias sobre Room y aislamiento de las excepciones por ocurrencia |
| `BackupSerializerTest` | Ida y vuelta del respaldo JSON sin perder informacion |

```bash
./gradlew test                  # 48 pruebas unitarias
./gradlew connectedAndroidTest  # pruebas sobre dispositivo o emulador
```

---

## 12. Pantallas

- **Inicio (Dashboard)**: ingresos del mes, tarjeta azul de la primera quincena,
  tarjeta naranja de la segunda, total del mes, alertas, resumen semanal, gastos
  pendientes y el boton *Copiar gastos al siguiente mes*.
- **Calendario**: rejilla mensual de domingo a sabado con los dias 1-15 en azul
  cielo, 16-fin en naranja, separacion visible entre quincenas e indicadores en
  los dias con movimientos. Al tocar un dia se muestran sus gastos y su total.
- **Gastos**: lista del mes agrupada por quincena con filtros, total y sobrante
  de cada bloque. Pulsacion larga: pagar, editar, duplicar o eliminar.
- **Ingresos**: lista del mes agrupada por quincena.
- **Quincena (detalle)**: ingresos, gastos, total, sobrante y accesos para
  agregar movimientos en ese periodo.
- **Estadisticas**: ingresos vs gastos, gasto por quincena, por semana,
  evolucion del sobrante y gasto por categoria.
- **Historial**: totales de cada mes del ano seleccionado.
- **Configuracion**: tema claro/oscuro/sistema, moneda, umbral de aviso,
  categorias, duplicar mes, datos de ejemplo, respaldo y borrado total.

El boton flotante `+` ofrece **NUEVO GASTO** y **NUEVO INGRESO**.

---

## 13. Moneda

Pesos mexicanos por defecto: `$13,600.00` con codigo `MXN`. El simbolo y el
codigo son configurables desde Ajustes para poder usar otra moneda sin tocar la
interfaz.
