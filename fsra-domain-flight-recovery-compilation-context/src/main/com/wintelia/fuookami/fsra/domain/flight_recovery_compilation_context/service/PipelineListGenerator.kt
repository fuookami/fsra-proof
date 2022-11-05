package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import fuookami.ospf.kotlin.framework.model.CGPipelineList
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.limits.*

class PipelineListGenerator(
    private val aggregation: Aggregation
) {
    operator fun invoke(
        cancelCostCalculator: CancelCostCalculator,
        delayCostCalculator: DelayCostCalculator,
        linkMap: FlightLinkMap,
        recoveryPlan: RecoveryPlan,
        configuration: Configuration,
        parameter: Parameter
    ): Result<CGPipelineList<LinearMetaModel, ShadowPriceMap>, Error> {
        val ret = ArrayList<CGPipeline<LinearMetaModel, ShadowPriceMap>>()

        ret.add(FlightTaskCompilationLimit(aggregation.recoveryNeededFlightTasks, aggregation.compilation, cancelCostCalculator))
        ret.add(AircraftCompilationLimit(aggregation.recoveryNeededAircrafts, aggregation.compilation, parameter))
        ret.add(FlowControlLimit(aggregation.flow, parameter))
        ret.add(FlightLinkLimit(linkMap, aggregation.compilation, aggregation.flightLink))
        ret.add(FleetBalanceLimit(aggregation.fleetBalance, parameter))

        if (configuration.withRedundancy) {
            ret.add(FlightTaskDelayLimit(aggregation.recoveryNeededFlightTasks, aggregation.flightTaskTime, recoveryPlan.timeWindow, delayCostCalculator))
        }

        return Ok(ret)
    }
}
