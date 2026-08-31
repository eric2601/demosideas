# Arquitectura

Documento de referencia para quien vaya a modificar el proyecto.

## 1. Capas

```
┌──────────────────────────────────────────┐
│ UI (Jetpack Compose)                     │  Solo pinta estado y emite eventos
│  DashboardScreen, CalendarScreen, ...    │
└──────────────────┬───────────────────────┘
                   │ StateFlow
┌──────────────────▼───────────────────────┐
│ ViewModel                                │  Estado de pantalla y acciones
│  MonthAwareViewModel + uno por pantalla  │
└──────────────────┬───────────────────────┘
                   │
┌──────────────────▼───────────────────────┐
│ Use Cases                                │  Composicion y validacion
│  GetMonthOverview, SaveExpense, ...      │
└──────────────────┬───────────────────────┘
                   │
┌──────────────────▼───────────────────────┐
│ Repository                               │  Fuente unica de verdad
│  BudgetRepository, SettingsRepository    │
└──────────────────┬───────────────────────┘
                   │
┌──────────────────▼───────────────────────┐
│ Room (SQLite local)                      │
└──────────────────────────────────────────┘
```

Reglas que el proyecto respeta:

1. Ningun `Composable` calcula dinero. Las cifras llegan ya calculadas.
2. Ningun `ViewModel` conoce Room. Solo habla con repositorios y casos de uso.
3. `FinancialCalculator`, `RecurrenceEngine`, `WeekCalculator`, `Money` y
   `Quincena` no importan nada de Android, y por eso se prueban en la JVM.
4. La quincena nunca se pide al usuario: se deriva de la fecha.

## 2. Modelo de datos

```
CategoryEntity ◄──────── ExpenseEntity ────────► RecurrenceEntity
                              │                        ▲
                              │                        │
                              ▼                        │
                  ExpenseOverrideEntity      IncomeEntity
                                                       │
                                                       ▼
                                            IncomeOverrideEntity

MonthEntity        UserSettingsEntity (fila unica, id = 1)
```

- `ExpenseEntity.date` es la fecha del movimiento si es unico, o la **fecha
  ancla** de la regla si es recurrente.
- `ExpenseEntity.recurrenceId` nulo significa "solo este mes".
- Los `*OverrideEntity` guardan cambios de **una sola ocurrencia**, identificada
  por `(movimientoId, occurrenceDate)`.

## 3. Del almacenamiento a la pantalla

```
ExpenseDao.observeCandidates(from, to)      Filas candidatas del rango
            │
            ▼
OccurrenceExpander.expandExpenses(...)      Aplica RecurrenceEngine y overrides
            │
            ▼
List<ExpenseOccurrence>                     Movimientos concretos del mes
            │
            ▼
FinancialCalculator.monthSummary(...)       Totales por quincena y del mes
            │
            ▼
MonthOverview                               Lo que consume la UI
```

`GetMonthOverviewUseCase` combina en un solo `Flow` los movimientos del mes y las
preferencias del usuario, de modo que un cambio de moneda o de umbral de aviso
repinta la pantalla igual que un gasto nuevo.

## 4. Decisiones y su motivo

| Decision | Motivo |
|---|---|
| Dinero en centavos (`Money`) | El punto flotante acumula error al sumar importes |
| Regla + expansion, en vez de una fila por mes | Evita duplicar informacion y que editar la serie deje meses desincronizados |
| Excepciones por ocurrencia | Permite pagar febrero sin tocar enero ni marzo |
| `@Upsert` en vez de `@Insert(REPLACE)` | REPLACE borra la fila y el `ON DELETE CASCADE` se llevaria las excepciones |
| Fechas como dia epoch | Ordena y compara en SQL, independiente de la zona horaria |
| Enums por nombre | Respaldo legible y tolerante a valores nuevos |
| DI manual (`AppContainer`) | Grafo pequeno; evita un procesador de anotaciones extra |
| Sin permiso `INTERNET` | Garantia estructural de que la informacion no sale del dispositivo |
| SAF para el respaldo | No hace falta permiso de almacenamiento |
| Desugaring de `java.time` | Permite `minSdk 24` sin renunciar a `LocalDate` y `YearMonth` |

## 5. Como agregar una frecuencia nueva

1. Agregar el valor a `Frequency` (`domain/model/Enums.kt`).
2. Implementar su estrategia en `RecurrenceEngine.occurrencesInMonth`.
3. Contemplarla en `SaveExpenseUseCase.buildRule`.
4. Anadir sus opciones al `ChipRow` de los formularios.
5. Escribir la prueba en `RecurrenceEngineTest`.

El conversor de Room no necesita cambios: los enums se guardan por nombre y el
valor desconocido cae en el valor por omision.

## 6. Como agregar una pantalla

1. Crear el `ViewModel` (heredar de `MonthAwareViewModel` si muestra un mes).
2. Registrar su factoria en `ViewModelFactories`.
3. Crear el `Composable` de la pantalla.
4. Anadir la ruta en `Routes` y el destino en `GastosNavHost`.
5. Si debe estar en la barra inferior, agregarla a `bottomDestinations`.
