package com.gastos.quincenal.data.repository

import androidx.room.withTransaction
import com.gastos.quincenal.data.local.AppDatabase
import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.ExpenseOverrideEntity
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.local.entity.IncomeOverrideEntity
import com.gastos.quincenal.data.local.entity.RecurrenceEntity
import com.gastos.quincenal.data.mapper.toDomain
import com.gastos.quincenal.data.mapper.toEntity
import com.gastos.quincenal.domain.model.Category
import com.gastos.quincenal.domain.model.ExpenseOccurrence
import com.gastos.quincenal.domain.model.IncomeOccurrence
import com.gastos.quincenal.domain.recurrence.RecurrenceRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

/** Todo lo que un mes necesita mostrar, resuelto en una sola emision. */
data class MonthData(
    val yearMonth: YearMonth,
    val expenses: List<ExpenseOccurrence>,
    val incomes: List<IncomeOccurrence>,
    val categories: List<Category>
)

/**
 * Fuente unica de verdad para gastos, ingresos y recurrencias.
 *
 * Expone flujos por mes ya expandidos; los ViewModels no saben nada de Room ni
 * del motor de recurrencia.
 */
class BudgetRepository(private val db: AppDatabase) {

    private val expenseDao = db.expenseDao()
    private val incomeDao = db.incomeDao()
    private val recurrenceDao = db.recurrenceDao()
    private val categoryDao = db.categoryDao()

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    fun observeMonth(yearMonth: YearMonth): Flow<MonthData> {
        val from = yearMonth.atDay(1).toEpochDay()
        val to = yearMonth.atEndOfMonth().toEpochDay()

        return combine(
            expenseDao.observeCandidates(from, to),
            incomeDao.observeCandidates(from, to),
            recurrenceDao.observeAll(),
            categoryDao.observeAll(),
            combine(
                expenseDao.observeOverrides(from, to),
                incomeDao.observeOverrides(from, to)
            ) { expenseOverrides, incomeOverrides -> expenseOverrides to incomeOverrides }
        ) { expenses, incomes, recurrences, categories, overrides ->
            val recurrenceIndex = recurrences.associateBy { it.id }
            val categoryIndex = categories.associateBy { it.id }

            MonthData(
                yearMonth = yearMonth,
                expenses = OccurrenceExpander.expandExpenses(
                    yearMonth = yearMonth,
                    expenses = expenses,
                    recurrences = recurrenceIndex,
                    categories = categoryIndex,
                    overrides = overrides.first
                ),
                incomes = OccurrenceExpander.expandIncomes(
                    yearMonth = yearMonth,
                    incomes = incomes,
                    recurrences = recurrenceIndex,
                    overrides = overrides.second
                ),
                categories = categories.map { it.toDomain() }
            )
        }
    }

    /** Version puntual (sin flujo) usada por el historial y las copias de mes. */
    suspend fun getMonth(yearMonth: YearMonth): MonthData {
        val from = yearMonth.atDay(1).toEpochDay()
        val to = yearMonth.atEndOfMonth().toEpochDay()

        val recurrences = recurrenceDao.getAll().associateBy { it.id }
        val categories = categoryDao.getAll().associateBy { it.id }
        val expenseOverrides = expenseDao.getAllOverrides().filter {
            it.occurrenceDate.toEpochDay() in from..to
        }
        val incomeOverrides = incomeDao.getAllOverrides().filter {
            it.occurrenceDate.toEpochDay() in from..to
        }

        return MonthData(
            yearMonth = yearMonth,
            expenses = OccurrenceExpander.expandExpenses(
                yearMonth = yearMonth,
                expenses = expenseDao.getCandidates(from, to),
                recurrences = recurrences,
                categories = categories,
                overrides = expenseOverrides
            ),
            incomes = OccurrenceExpander.expandIncomes(
                yearMonth = yearMonth,
                incomes = incomeDao.getCandidates(from, to),
                recurrences = recurrences,
                overrides = incomeOverrides
            ),
            categories = categories.values.map { it.toDomain() }
        )
    }

    fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getExpense(id: Long): ExpenseEntity? = expenseDao.getById(id)

    suspend fun getIncome(id: Long): IncomeEntity? = incomeDao.getById(id)

    suspend fun getRecurrence(id: Long): RecurrenceRule? =
        recurrenceDao.getById(id)?.toDomain()

    // ------------------------------------------------------------------
    // Escritura de gastos
    // ------------------------------------------------------------------

    /**
     * Crea o actualiza un gasto junto con su regla de recurrencia.
     * Se ejecuta en una transaccion para que nunca quede un gasto apuntando a
     * una regla que no llego a guardarse.
     */
    suspend fun saveExpense(expense: ExpenseEntity, rule: RecurrenceRule?): Long =
        db.withTransaction {
            val previousRecurrenceId = expense.id
                .takeIf { it != 0L }
                ?.let { expenseDao.getById(it)?.recurrenceId }

            val recurrenceId = when {
                rule == null -> null
                else -> {
                    val entity = RecurrenceEntity(
                        id = previousRecurrenceId ?: 0L,
                        frequency = rule.frequency,
                        interval = rule.interval,
                        startDate = rule.startDate,
                        endDate = rule.endDate,
                        dayOfMonth = rule.dayOfMonth,
                        dayOfWeek = rule.dayOfWeek,
                        active = rule.active
                    )
                    recurrenceDao.upsert(entity)
                }
            }

            // Si el gasto dejo de ser recurrente se borra la regla huerfana.
            if (rule == null && previousRecurrenceId != null) {
                recurrenceDao.deleteById(previousRecurrenceId)
            }

            val toStore = expense.copy(
                recurrenceId = recurrenceId,
                isRecurring = recurrenceId != null,
                updatedAt = System.currentTimeMillis()
            )
            expenseDao.upsert(toStore)
        }

    suspend fun deleteExpense(id: Long) = db.withTransaction {
        val recurrenceId = expenseDao.getById(id)?.recurrenceId
        expenseDao.deleteById(id)
        recurrenceId?.let { recurrenceDao.deleteById(it) }
    }

    /** Duplica un gasto conservando su configuracion pero como movimiento nuevo. */
    suspend fun duplicateExpense(id: Long): Long? = db.withTransaction {
        val original = expenseDao.getById(id) ?: return@withTransaction null
        val rule = original.recurrenceId?.let { recurrenceDao.getById(it)?.toDomain() }
        saveExpense(
            expense = original.copy(
                id = 0L,
                name = "${original.name} (copia)",
                isPaid = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            rule = rule?.copy(id = 0L)
        )
    }

    /**
     * Marca una ocurrencia como pagada o pendiente.
     *
     * En un gasto unico se actualiza la propia fila. En uno recurrente se guarda
     * una excepcion para esa fecha, de modo que los demas meses no cambian.
     */
    suspend fun setExpensePaid(expenseId: Long, date: LocalDate, paid: Boolean) =
        db.withTransaction {
            val expense = expenseDao.getById(expenseId) ?: return@withTransaction
            if (expense.recurrenceId == null) {
                expenseDao.update(expense.copy(isPaid = paid, updatedAt = System.currentTimeMillis()))
            } else {
                upsertExpenseOverride(expenseId, date) { it.copy(isPaid = paid) }
            }
        }

    /** Cambia el importe de una sola ocurrencia sin tocar el resto de la serie. */
    suspend fun setExpenseAmountForOccurrence(expenseId: Long, date: LocalDate, cents: Long) =
        db.withTransaction {
            val expense = expenseDao.getById(expenseId) ?: return@withTransaction
            if (expense.recurrenceId == null) {
                expenseDao.update(expense.copy(amountCents = cents, updatedAt = System.currentTimeMillis()))
            } else {
                upsertExpenseOverride(expenseId, date) { it.copy(amountCents = cents) }
            }
        }

    /** Mueve una sola ocurrencia a otra fecha. */
    suspend fun moveExpenseOccurrence(expenseId: Long, date: LocalDate, newDate: LocalDate) =
        db.withTransaction {
            val expense = expenseDao.getById(expenseId) ?: return@withTransaction
            if (expense.recurrenceId == null) {
                expenseDao.update(expense.copy(date = newDate, updatedAt = System.currentTimeMillis()))
            } else {
                upsertExpenseOverride(expenseId, date) { it.copy(movedToDate = newDate) }
            }
        }

    /** Omite una ocurrencia concreta de un gasto recurrente. */
    suspend fun skipExpenseOccurrence(expenseId: Long, date: LocalDate) = db.withTransaction {
        val expense = expenseDao.getById(expenseId) ?: return@withTransaction
        if (expense.recurrenceId == null) {
            expenseDao.deleteById(expenseId)
        } else {
            upsertExpenseOverride(expenseId, date) { it.copy(skipped = true) }
        }
    }

    // ------------------------------------------------------------------
    // Escritura de ingresos
    // ------------------------------------------------------------------

    suspend fun saveIncome(income: IncomeEntity, rule: RecurrenceRule?): Long = db.withTransaction {
        val previousRecurrenceId = income.id
            .takeIf { it != 0L }
            ?.let { incomeDao.getById(it)?.recurrenceId }

        val recurrenceId = when {
            rule == null -> null
            else -> {
                val entity = RecurrenceEntity(
                    id = previousRecurrenceId ?: 0L,
                    frequency = rule.frequency,
                    interval = rule.interval,
                    startDate = rule.startDate,
                    endDate = rule.endDate,
                    dayOfMonth = rule.dayOfMonth,
                    dayOfWeek = rule.dayOfWeek,
                    active = rule.active
                )
                recurrenceDao.upsert(entity)
            }
        }

        if (rule == null && previousRecurrenceId != null) {
            recurrenceDao.deleteById(previousRecurrenceId)
        }

        incomeDao.upsert(
            income.copy(
                recurrenceId = recurrenceId,
                isRecurring = recurrenceId != null,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteIncome(id: Long) = db.withTransaction {
        val recurrenceId = incomeDao.getById(id)?.recurrenceId
        incomeDao.deleteById(id)
        recurrenceId?.let { recurrenceDao.deleteById(it) }
    }

    suspend fun duplicateIncome(id: Long): Long? = db.withTransaction {
        val original = incomeDao.getById(id) ?: return@withTransaction null
        val rule = original.recurrenceId?.let { recurrenceDao.getById(it)?.toDomain() }
        saveIncome(
            income = original.copy(
                id = 0L,
                name = "${original.name} (copia)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ),
            rule = rule?.copy(id = 0L)
        )
    }

    suspend fun setIncomeReceived(incomeId: Long, date: LocalDate, received: Boolean) =
        db.withTransaction {
            val income = incomeDao.getById(incomeId) ?: return@withTransaction
            if (income.recurrenceId == null) {
                incomeDao.update(income.copy(isReceived = received, updatedAt = System.currentTimeMillis()))
            } else {
                upsertIncomeOverride(incomeId, date) { it.copy(isReceived = received) }
            }
        }

    suspend fun skipIncomeOccurrence(incomeId: Long, date: LocalDate) = db.withTransaction {
        val income = incomeDao.getById(incomeId) ?: return@withTransaction
        if (income.recurrenceId == null) {
            incomeDao.deleteById(incomeId)
        } else {
            upsertIncomeOverride(incomeId, date) { it.copy(skipped = true) }
        }
    }

    // ------------------------------------------------------------------
    // Categorias
    // ------------------------------------------------------------------

    suspend fun saveCategory(category: Category): Long = categoryDao.upsert(category.toEntity())

    suspend fun deleteCategory(category: Category) = categoryDao.delete(category.toEntity())

    // ------------------------------------------------------------------
    // Excepciones por ocurrencia
    // ------------------------------------------------------------------

    private suspend fun upsertExpenseOverride(
        expenseId: Long,
        date: LocalDate,
        transform: (ExpenseOverrideEntity) -> ExpenseOverrideEntity
    ) {
        val epochDay = date.toEpochDay()
        val current = expenseDao.getOverride(expenseId, epochDay)
            ?: ExpenseOverrideEntity(expenseId = expenseId, occurrenceDate = date)
        val updated = transform(current)
        if (updated.isEmpty()) {
            expenseDao.deleteOverride(expenseId, epochDay)
        } else {
            expenseDao.upsertOverride(updated)
        }
    }

    private suspend fun upsertIncomeOverride(
        incomeId: Long,
        date: LocalDate,
        transform: (IncomeOverrideEntity) -> IncomeOverrideEntity
    ) {
        val epochDay = date.toEpochDay()
        val current = incomeDao.getOverride(incomeId, epochDay)
            ?: IncomeOverrideEntity(incomeId = incomeId, occurrenceDate = date)
        val updated = transform(current)
        if (updated.isEmpty()) {
            incomeDao.deleteOverride(incomeId, epochDay)
        } else {
            incomeDao.upsertOverride(updated)
        }
    }

    private fun ExpenseOverrideEntity.isEmpty(): Boolean =
        amountCents == null && isPaid == null && movedToDate == null && notes == null && !skipped

    private fun IncomeOverrideEntity.isEmpty(): Boolean =
        amountCents == null && isReceived == null && movedToDate == null && notes == null && !skipped
}
