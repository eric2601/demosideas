package com.gastos.quincenal.data.mapper

import com.gastos.quincenal.data.local.entity.CategoryEntity
import com.gastos.quincenal.data.local.entity.RecurrenceEntity
import com.gastos.quincenal.domain.model.Category
import com.gastos.quincenal.domain.recurrence.RecurrenceRule

/** Conversiones entre entidades de Room y modelos de dominio. */

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    colorArgb = colorArgb,
    forIncome = forIncome,
    isDefault = isDefault
)

fun Category.toEntity(createdAt: Long = System.currentTimeMillis()): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    colorArgb = colorArgb,
    forIncome = forIncome,
    isDefault = isDefault,
    createdAt = createdAt
)

fun RecurrenceEntity.toDomain(): RecurrenceRule = RecurrenceRule(
    id = id,
    frequency = frequency,
    interval = interval,
    startDate = startDate,
    endDate = endDate,
    dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek,
    active = active
)

fun RecurrenceRule.toEntity(): RecurrenceEntity = RecurrenceEntity(
    id = id,
    frequency = frequency,
    interval = interval,
    startDate = startDate,
    endDate = endDate,
    dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek,
    active = active
)
