package com.gastos.quincenal.data.backup

import com.gastos.quincenal.data.local.entity.CategoryEntity
import com.gastos.quincenal.data.local.entity.ExpenseEntity
import com.gastos.quincenal.data.local.entity.ExpenseOverrideEntity
import com.gastos.quincenal.data.local.entity.IncomeEntity
import com.gastos.quincenal.data.local.entity.IncomeOverrideEntity
import com.gastos.quincenal.data.local.entity.MonthEntity
import com.gastos.quincenal.data.local.entity.RecurrenceEntity
import com.gastos.quincenal.data.local.entity.UserSettingsEntity
import com.gastos.quincenal.domain.model.ExpenseType
import com.gastos.quincenal.domain.model.Frequency
import com.gastos.quincenal.domain.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/** Contenido completo de un respaldo. */
data class BackupPayload(
    val version: Int,
    val exportedAt: Long,
    val settings: UserSettingsEntity,
    val categories: List<CategoryEntity>,
    val recurrences: List<RecurrenceEntity>,
    val expenses: List<ExpenseEntity>,
    val incomes: List<IncomeEntity>,
    val expenseOverrides: List<ExpenseOverrideEntity>,
    val incomeOverrides: List<IncomeOverrideEntity>,
    val months: List<MonthEntity>
) {
    fun stats() = BackupStats(
        categories = categories.size,
        recurrences = recurrences.size,
        expenses = expenses.size,
        incomes = incomes.size,
        expenseOverrides = expenseOverrides.size,
        incomeOverrides = incomeOverrides.size,
        months = months.size
    )
}

/**
 * Serializa y lee el respaldo en JSON usando org.json, que forma parte del
 * propio Android: no se agrega ninguna dependencia externa ni sale informacion
 * del dispositivo.
 *
 * Las fechas se escriben en ISO ("2026-01-15") para que el archivo sea legible
 * y siga siendo valido si cambia la representacion interna.
 */
object BackupSerializer {

    const val CURRENT_VERSION = 1

    fun encode(payload: BackupPayload): String = JSONObject().apply {
        put("version", payload.version)
        put("exportedAt", payload.exportedAt)
        put("app", "Control de Gastos Quincenal")
        put("settings", encodeSettings(payload.settings))
        put("categories", payload.categories.map(::encodeCategory).toJsonArray())
        put("recurrences", payload.recurrences.map(::encodeRecurrence).toJsonArray())
        put("expenses", payload.expenses.map(::encodeExpense).toJsonArray())
        put("incomes", payload.incomes.map(::encodeIncome).toJsonArray())
        put("expenseOverrides", payload.expenseOverrides.map(::encodeExpenseOverride).toJsonArray())
        put("incomeOverrides", payload.incomeOverrides.map(::encodeIncomeOverride).toJsonArray())
        put("months", payload.months.map(::encodeMonth).toJsonArray())
    }.toString(2)

    /** Lanza [org.json.JSONException] si el texto no es un respaldo valido. */
    fun decode(text: String): BackupPayload {
        val root = JSONObject(text)
        return BackupPayload(
            version = root.optInt("version", CURRENT_VERSION),
            exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
            settings = decodeSettings(root.optJSONObject("settings")),
            categories = root.array("categories").mapObjects(::decodeCategory),
            recurrences = root.array("recurrences").mapObjects(::decodeRecurrence),
            expenses = root.array("expenses").mapObjects(::decodeExpense),
            incomes = root.array("incomes").mapObjects(::decodeIncome),
            expenseOverrides = root.array("expenseOverrides").mapObjects(::decodeExpenseOverride),
            incomeOverrides = root.array("incomeOverrides").mapObjects(::decodeIncomeOverride),
            months = root.array("months").mapObjects(::decodeMonth)
        )
    }

    // ------------------------------------------------------------------
    // Codificacion
    // ------------------------------------------------------------------

    private fun encodeSettings(settings: UserSettingsEntity) = JSONObject().apply {
        put("currencySymbol", settings.currencySymbol)
        put("currencyCode", settings.currencyCode)
        put("themeMode", settings.themeMode.name)
        put("lowRemainingThresholdCents", settings.lowRemainingThresholdCents)
        put("demoDataLoaded", settings.demoDataLoaded)
        put("onboardingCompleted", settings.onboardingCompleted)
    }

    private fun encodeCategory(category: CategoryEntity) = JSONObject().apply {
        put("id", category.id)
        put("name", category.name)
        put("colorArgb", category.colorArgb)
        put("forIncome", category.forIncome)
        put("isDefault", category.isDefault)
        put("createdAt", category.createdAt)
    }

    private fun encodeRecurrence(recurrence: RecurrenceEntity) = JSONObject().apply {
        put("id", recurrence.id)
        put("frequency", recurrence.frequency.name)
        put("interval", recurrence.interval)
        put("startDate", recurrence.startDate.toString())
        putDate("endDate", recurrence.endDate)
        putIntOrNull("dayOfMonth", recurrence.dayOfMonth)
        putIntOrNull("dayOfWeek", recurrence.dayOfWeek?.value)
        put("active", recurrence.active)
    }

    private fun encodeExpense(expense: ExpenseEntity) = JSONObject().apply {
        put("id", expense.id)
        put("name", expense.name)
        put("amountCents", expense.amountCents)
        putLongOrNull("categoryId", expense.categoryId)
        put("date", expense.date.toString())
        put("type", expense.type.name)
        put("isRecurring", expense.isRecurring)
        putLongOrNull("recurrenceId", expense.recurrenceId)
        put("isPaid", expense.isPaid)
        put("notes", expense.notes)
        put("active", expense.active)
        put("isDemo", expense.isDemo)
        put("createdAt", expense.createdAt)
        put("updatedAt", expense.updatedAt)
    }

    private fun encodeIncome(income: IncomeEntity) = JSONObject().apply {
        put("id", income.id)
        put("name", income.name)
        put("amountCents", income.amountCents)
        put("date", income.date.toString())
        put("isRecurring", income.isRecurring)
        putLongOrNull("recurrenceId", income.recurrenceId)
        put("isReceived", income.isReceived)
        put("notes", income.notes)
        put("active", income.active)
        put("isDemo", income.isDemo)
        put("createdAt", income.createdAt)
        put("updatedAt", income.updatedAt)
    }

    private fun encodeExpenseOverride(override: ExpenseOverrideEntity) = JSONObject().apply {
        put("id", override.id)
        put("expenseId", override.expenseId)
        put("occurrenceDate", override.occurrenceDate.toString())
        putLongOrNull("amountCents", override.amountCents)
        putBooleanOrNull("isPaid", override.isPaid)
        putDate("movedToDate", override.movedToDate)
        putStringOrNull("notes", override.notes)
        put("skipped", override.skipped)
    }

    private fun encodeIncomeOverride(override: IncomeOverrideEntity) = JSONObject().apply {
        put("id", override.id)
        put("incomeId", override.incomeId)
        put("occurrenceDate", override.occurrenceDate.toString())
        putLongOrNull("amountCents", override.amountCents)
        putBooleanOrNull("isReceived", override.isReceived)
        putDate("movedToDate", override.movedToDate)
        putStringOrNull("notes", override.notes)
        put("skipped", override.skipped)
    }

    private fun encodeMonth(month: MonthEntity) = JSONObject().apply {
        put("yearMonth", month.yearMonth)
        put("note", month.note)
        put("closed", month.closed)
        put("createdAt", month.createdAt)
    }

    // ------------------------------------------------------------------
    // Decodificacion
    // ------------------------------------------------------------------

    private fun decodeSettings(json: JSONObject?): UserSettingsEntity {
        if (json == null) return UserSettingsEntity()
        return UserSettingsEntity(
            currencySymbol = json.optString("currencySymbol", "$"),
            currencyCode = json.optString("currencyCode", "MXN"),
            themeMode = runCatching { ThemeMode.valueOf(json.optString("themeMode")) }
                .getOrDefault(ThemeMode.SYSTEM),
            lowRemainingThresholdCents = json.optLong("lowRemainingThresholdCents", 50_000L),
            demoDataLoaded = json.optBoolean("demoDataLoaded", false),
            onboardingCompleted = json.optBoolean("onboardingCompleted", false)
        )
    }

    private fun decodeCategory(json: JSONObject) = CategoryEntity(
        id = json.optLong("id"),
        name = json.optString("name"),
        colorArgb = json.optInt("colorArgb"),
        forIncome = json.optBoolean("forIncome", false),
        isDefault = json.optBoolean("isDefault", false),
        createdAt = json.optLong("createdAt", System.currentTimeMillis())
    )

    private fun decodeRecurrence(json: JSONObject) = RecurrenceEntity(
        id = json.optLong("id"),
        frequency = runCatching { Frequency.valueOf(json.optString("frequency")) }
            .getOrDefault(Frequency.ONCE),
        interval = json.optInt("interval", 1).coerceAtLeast(1),
        startDate = json.date("startDate") ?: LocalDate.now(),
        endDate = json.date("endDate"),
        dayOfMonth = json.intOrNull("dayOfMonth"),
        dayOfWeek = json.intOrNull("dayOfWeek")?.takeIf { it in 1..7 }?.let(DayOfWeek::of),
        active = json.optBoolean("active", true)
    )

    private fun decodeExpense(json: JSONObject) = ExpenseEntity(
        id = json.optLong("id"),
        name = json.optString("name"),
        amountCents = json.optLong("amountCents"),
        categoryId = json.longOrNull("categoryId"),
        date = json.date("date") ?: LocalDate.now(),
        type = runCatching { ExpenseType.valueOf(json.optString("type")) }
            .getOrDefault(ExpenseType.VARIABLE),
        isRecurring = json.optBoolean("isRecurring", false),
        recurrenceId = json.longOrNull("recurrenceId"),
        isPaid = json.optBoolean("isPaid", false),
        notes = json.optString("notes"),
        active = json.optBoolean("active", true),
        isDemo = json.optBoolean("isDemo", false),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun decodeIncome(json: JSONObject) = IncomeEntity(
        id = json.optLong("id"),
        name = json.optString("name"),
        amountCents = json.optLong("amountCents"),
        date = json.date("date") ?: LocalDate.now(),
        isRecurring = json.optBoolean("isRecurring", false),
        recurrenceId = json.longOrNull("recurrenceId"),
        isReceived = json.optBoolean("isReceived", false),
        notes = json.optString("notes"),
        active = json.optBoolean("active", true),
        isDemo = json.optBoolean("isDemo", false),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun decodeExpenseOverride(json: JSONObject) = ExpenseOverrideEntity(
        id = json.optLong("id"),
        expenseId = json.optLong("expenseId"),
        occurrenceDate = json.date("occurrenceDate") ?: LocalDate.now(),
        amountCents = json.longOrNull("amountCents"),
        isPaid = json.booleanOrNull("isPaid"),
        movedToDate = json.date("movedToDate"),
        notes = json.stringOrNull("notes"),
        skipped = json.optBoolean("skipped", false)
    )

    private fun decodeIncomeOverride(json: JSONObject) = IncomeOverrideEntity(
        id = json.optLong("id"),
        incomeId = json.optLong("incomeId"),
        occurrenceDate = json.date("occurrenceDate") ?: LocalDate.now(),
        amountCents = json.longOrNull("amountCents"),
        isReceived = json.booleanOrNull("isReceived"),
        movedToDate = json.date("movedToDate"),
        notes = json.stringOrNull("notes"),
        skipped = json.optBoolean("skipped", false)
    )

    private fun decodeMonth(json: JSONObject) = MonthEntity(
        yearMonth = json.optString("yearMonth"),
        note = json.optString("note"),
        closed = json.optBoolean("closed", false),
        createdAt = json.optLong("createdAt", System.currentTimeMillis())
    )

    // ------------------------------------------------------------------
    // Ayudas para trabajar con org.json sin repetir comprobaciones de nulos
    // ------------------------------------------------------------------

    private fun List<JSONObject>.toJsonArray(): JSONArray =
        JSONArray().also { array -> forEach(array::put) }

    private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { optJSONObject(it) }.map(transform)

    private fun JSONObject.putDate(name: String, date: LocalDate?) {
        if (date == null) put(name, JSONObject.NULL) else put(name, date.toString())
    }

    private fun JSONObject.putIntOrNull(name: String, value: Int?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }

    private fun JSONObject.putLongOrNull(name: String, value: Long?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }

    private fun JSONObject.putBooleanOrNull(name: String, value: Boolean?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }

    private fun JSONObject.putStringOrNull(name: String, value: String?) {
        if (value == null) put(name, JSONObject.NULL) else put(name, value)
    }

    private fun JSONObject.date(name: String): LocalDate? {
        if (isNull(name)) return null
        return runCatching { LocalDate.parse(optString(name)) }.getOrNull()
    }

    private fun JSONObject.intOrNull(name: String): Int? = if (isNull(name)) null else optInt(name)

    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else optLong(name)

    private fun JSONObject.booleanOrNull(name: String): Boolean? =
        if (isNull(name)) null else optBoolean(name)

    private fun JSONObject.stringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name)
}
