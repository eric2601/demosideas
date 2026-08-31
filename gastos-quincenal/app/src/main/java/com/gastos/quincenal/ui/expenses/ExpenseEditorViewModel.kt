package com.gastos.quincenal.ui.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.data.repository.SettingsRepository
import com.gastos.quincenal.domain.model.Category
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.domain.model.RecurrenceEnd
import com.gastos.quincenal.domain.usecase.ExpenseInput
import com.gastos.quincenal.domain.usecase.SaveExpenseUseCase
import com.gastos.quincenal.domain.usecase.SaveResult
import com.gastos.quincenal.utils.CurrencyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

/** Estado del formulario de gasto. */
data class ExpenseEditorState(
    val id: Long = 0L,
    val name: String = "",
    val amountText: String = "",
    val categoryId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    val type: ExpenseType = ExpenseType.VARIABLE,
    val frequency: Frequency = Frequency.ONCE,
    val recurrenceEnd: RecurrenceEnd = RecurrenceEnd.ONLY_THIS_MONTH,
    val endDate: LocalDate? = null,
    val dayOfWeek: DayOfWeek? = null,
    val isPaid: Boolean = false,
    val notes: String = "",
    val categories: List<Category> = emptyList(),
    val errors: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val formatter: CurrencyFormatter = CurrencyFormatter.MXN
) {
    val isEditing: Boolean get() = id != 0L

    /** La quincena se deduce de la fecha; el usuario nunca la elige a mano. */
    val quincena: Quincena get() = Quincena.of(date)

    /** Importe interpretado del texto, o `null` si aun no es valido. */
    val amount: Money? get() = Money.parse(amountText)

    val showDayOfWeekPicker: Boolean
        get() = frequency.needsDayOfWeek

    val showEndDatePicker: Boolean
        get() = recurrenceEnd == RecurrenceEnd.UNTIL_DATE
}

/**
 * Formulario de alta y edicion de gastos.
 *
 * Toda la validacion vive en el caso de uso; aqui solo se mantiene el estado del
 * formulario y se traduce el resultado a mensajes para la pantalla.
 */
class ExpenseEditorViewModel(
    private val budgetRepository: BudgetRepository,
    private val saveExpense: SaveExpenseUseCase,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ExpenseEditorState())
    val state: StateFlow<ExpenseEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            budgetRepository.observeCategories().collect { categories ->
                _state.update { current ->
                    current.copy(
                        categories = categories.filterNot { it.forIncome },
                        categoryId = current.categoryId
                            ?: categories.firstOrNull { !it.forIncome }?.id
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _state.update { it.copy(formatter = settings.formatter()) }
            }
        }
    }

    /**
     * Prepara el formulario. Con [expenseId] distinto de cero carga el gasto
     * existente; con cero abre uno nuevo en la fecha indicada.
     */
    fun start(expenseId: Long, initialDate: LocalDate?) {
        if (_state.value.id == expenseId && expenseId != 0L) return

        if (expenseId == 0L) {
            _state.update {
                it.copy(
                    id = 0L,
                    name = "",
                    amountText = "",
                    date = initialDate ?: LocalDate.now(),
                    frequency = Frequency.ONCE,
                    recurrenceEnd = RecurrenceEnd.ONLY_THIS_MONTH,
                    endDate = null,
                    dayOfWeek = null,
                    isPaid = false,
                    notes = "",
                    errors = emptyList(),
                    isSaved = false
                )
            }
            return
        }

        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val expense = budgetRepository.getExpense(expenseId)
            if (expense == null) {
                _state.update {
                    it.copy(isLoading = false, errors = listOf("El gasto ya no existe."))
                }
                return@launch
            }
            val rule = expense.recurrenceId?.let { budgetRepository.getRecurrence(it) }

            _state.update { current ->
                current.copy(
                    id = expense.id,
                    name = expense.name,
                    amountText = current.formatter.formatPlain(Money(expense.amountCents)),
                    categoryId = expense.categoryId,
                    date = expense.date,
                    type = expense.type,
                    frequency = rule?.frequency ?: Frequency.ONCE,
                    recurrenceEnd = when {
                        rule == null -> RecurrenceEnd.ONLY_THIS_MONTH
                        rule.endDate != null -> RecurrenceEnd.UNTIL_DATE
                        else -> RecurrenceEnd.FOREVER
                    },
                    endDate = rule?.endDate,
                    dayOfWeek = rule?.dayOfWeek,
                    isPaid = expense.isPaid,
                    notes = expense.notes,
                    errors = emptyList(),
                    isLoading = false,
                    isSaved = false
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, errors = emptyList()) }

    fun setAmount(value: String) = _state.update { it.copy(amountText = value, errors = emptyList()) }

    fun setCategory(categoryId: Long?) = _state.update { it.copy(categoryId = categoryId) }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date, errors = emptyList()) }

    fun setType(type: ExpenseType) = _state.update { it.copy(type = type) }

    /**
     * Al elegir la frecuencia se ajusta el fin de la recurrencia para que las
     * dos opciones sean siempre coherentes entre si.
     */
    fun setFrequency(frequency: Frequency) = _state.update { current ->
        current.copy(
            frequency = frequency,
            recurrenceEnd = when {
                frequency == Frequency.ONCE -> RecurrenceEnd.ONLY_THIS_MONTH
                current.recurrenceEnd == RecurrenceEnd.ONLY_THIS_MONTH -> RecurrenceEnd.FOREVER
                else -> current.recurrenceEnd
            },
            dayOfWeek = if (frequency.needsDayOfWeek) {
                current.dayOfWeek ?: current.date.dayOfWeek
            } else {
                null
            },
            errors = emptyList()
        )
    }

    fun setRecurrenceEnd(end: RecurrenceEnd) = _state.update { current ->
        current.copy(
            recurrenceEnd = end,
            frequency = if (end != RecurrenceEnd.ONLY_THIS_MONTH && current.frequency == Frequency.ONCE) {
                Frequency.MONTHLY
            } else {
                current.frequency
            },
            endDate = if (end == RecurrenceEnd.UNTIL_DATE) {
                current.endDate ?: current.date.plusYears(1)
            } else {
                null
            },
            errors = emptyList()
        )
    }

    fun setEndDate(date: LocalDate?) = _state.update { it.copy(endDate = date, errors = emptyList()) }

    fun setDayOfWeek(dayOfWeek: DayOfWeek) = _state.update { it.copy(dayOfWeek = dayOfWeek) }

    fun setPaid(paid: Boolean) = _state.update { it.copy(isPaid = paid) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun save() {
        val current = _state.value
        val amount = current.amount
        if (amount == null) {
            _state.update { it.copy(errors = listOf("Escribe una cantidad valida, por ejemplo 1,250.00")) }
            return
        }

        _state.update { it.copy(isSaving = true, errors = emptyList()) }
        viewModelScope.launch {
            val result = saveExpense(
                ExpenseInput(
                    id = current.id,
                    name = current.name,
                    amount = amount,
                    categoryId = current.categoryId,
                    date = current.date,
                    type = current.type,
                    frequency = current.frequency,
                    recurrenceEnd = current.recurrenceEnd,
                    endDate = current.endDate,
                    dayOfWeek = current.dayOfWeek,
                    isPaid = current.isPaid,
                    notes = current.notes
                )
            )
            when (result) {
                is SaveResult.Success -> _state.update {
                    it.copy(isSaving = false, isSaved = true, id = result.id)
                }

                is SaveResult.Invalid -> _state.update {
                    it.copy(isSaving = false, errors = result.errors)
                }
            }
        }
    }
}
