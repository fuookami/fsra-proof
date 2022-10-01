package com.wintelia.fuookami.fsra.domain.rule_context.model

import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

data class WeakLimitKey(
    val from: Airport,
    val to: Airport,
    val aircraft: Aircraft
)

data class WeakLimit(
    val key: WeakLimitKey,
    val weight: Flt64,
) {
}
