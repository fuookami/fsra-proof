package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class FreeAircraftSelector(
    val aggregation: Aggregation
) {
    operator fun invoke(fixedBunches: Set<FlightTaskBunch>, hiddenAircrafts: Set<Aircraft>, flowControls: List<FlowControl>, shadowPriceMap: ShadowPriceMap, model: LinearMetaModel): Result<Set<Aircraft>, Error> {
        // todo
        return Ok(emptySet())
    }
}
