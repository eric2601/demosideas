package com.gastos.quincenal.ui.statistics

import com.gastos.quincenal.domain.usecase.GetMonthOverviewUseCase
import com.gastos.quincenal.ui.common.MonthAwareViewModel

/** Estadisticas del mes: solo consulta, sin escritura. */
class StatisticsViewModel(
    getMonthOverview: GetMonthOverviewUseCase
) : MonthAwareViewModel(getMonthOverview)
