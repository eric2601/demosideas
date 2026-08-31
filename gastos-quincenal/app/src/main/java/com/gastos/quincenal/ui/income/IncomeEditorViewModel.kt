package com.gastos.quincenal.ui.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gastos.quincenal.data.repository.BudgetRepository
import com.gastos.quincenal.data.repository.SettingsRepository
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.Money
import com.gastos.quincenal.domain.model.Quincena
import com.gastos.quincenal.domain.model.RecurrenceEnd
import com.gastos.quincenal.domain.usecase.IncomeInput
import com.gastos.quincenal.domain.usecase.SaveIncomeUseCase
import com.gastos.quincenal.domain.usecase.SaveResult
import com.gastos.quincenal.utils.CurrencyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Estado del formulario de ingreso. */
data class IncomeEditorState(
    val id: Long = 0L,
    val name: String = "",
    val amountText: String = "",
    val date: LocalDate = LocalDate.now(),
    val frequency: Frequency = Frequency.ONCE,
    val recurrenceEnd: RecurrenceEnd = RecurrenceEnd.ONLY_THIS_MONTH,
    val endDate: LocalDate? = null,
    val isReceived: Boolean = false,
    val notes: String = "",
    val errors: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    val formatter: CurrencyFormatter = CurrencyFormatter.MXN
) {
    val isEditing: Boolean get() = id != 0L
    val quincena: Quincena get() = Quincena.of(date)
    val amount: Money? get() = Money.parse(amountText)
    val showEndDatePicker: Boolean get() = recurrenceEnd == RecurrenceEnd.UNTIL_DATE
}

/** Formulario de alta y edicion de ingresos. */
class IncomeEditorViewModel(
    private val budgetRepository: BudgetRepository,
    private val saveIncome: SaveIncomeUseCase,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(IncomeEditorState())
    val state: StateFlow<IncomeEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _state.update { it.copy(formatter = settings.formatter()) }
            }
        }
    }

    fun start(incomeId: Long, initialDate: LocalDate?) {
        if (_state.value.id == incomeId && incomeId != 0L) return

        if (incomeId == 0L) {
            _state.update {
                it.copy(
                    id = 0L,
                    name = "",
                    amountText = "",
                    date = initialDate ?: LocalDate.now(),
                    frequency = Frequency.ONCE,
                    recurrenceEnd = RecurrenceEnd.ONLY_THIS_MONTH,
                    endDate = null,
                    isReceived = false,
                    notes = "",
                    errors = emptyList(),
                    isSaved = false
                )
            }
            return
        }

        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val income = budgetRepository.getIncome(incomeId)
            if (income == null) {
                _state.update {
                    it.copy(isLoading = false, errors = listOf("El ingreso ya no existe."))
                }
                return@launch
            }
            val rule = income.recurrenceId?.let { budgetRepository.getRecurrence(it) }

            _state.update { current ->
                current.copy(
                    id = income.id,
                    name = income.name,
                    amountText = current.formatter.formatPlain(Money(income.amountCents)),
                    date = income.date,
                    frequency = rule?.frequency ?: Frequency.ONCE,
                    recurrenceEnd = when {
                        rule == null -> RecurrenceEnd.ONLY_THIS_MONTH
                        rule.endDate != null -> RecurrenceEnd.UNTIL_DATE
                        else -> RecurrenceEnd.FOREVER
                    },
                    endDate = rule?.endDate,
                    isReceived = income.isReceived,
                    notes = income.notes,
                    errors = emptyList(),
                    isLoading = false,
                    isSaved = false
                )
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, errors = emptyList()) }

    fun setAmount(value: String) = _state.update { it.copy(amountText = value, errors = emptyList()) }

    fun setDate(date: LocalDate) = _state.update { it.copy(date = date, errors = emptyList()) }

    fun setFrequency(frequency: Frequency) = _state.update { current ->
        current.copy(
            frequency = frequency,
            recurrenceEnd = when {
                frequency == Frequency.ONCE -> RecurrenceEnd.ONLY_THIS_MONTH
                current.recurrenceEnd == RecurrenceEnd.ONLY_THIS_MONTH -> RecurrenceEnd.FOREVER
                else -> current.recurrenceEnd
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

    fun setReceived(received: Boolean) = _state.update { it.copy(isReceived = received) }

    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun save() {
        val current = _state.value
        val amount = current.amount
        if (amount == null) {
            _state.update { it.copy(errors = listOf("Escribe una cantidad valida, por ejemplo 13,600.00")) }
            return
        }

        _state.update { it.copy(isSaving = true, errors = emptyList()) }
        viewModelScope.launch {
            val result = saveIncome(
                IncomeInput(
                    id = current.id,
                    name = current.name,
                    amount = amount,
                    date = current.date,
                    frequency = current.frequency,
                    recurrenceEnd = current.recurrenceEnd,
                    endDate = current.endDate,
                    isReceived = current.isReceived,
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
