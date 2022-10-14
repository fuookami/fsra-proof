package com.wintelia.fuookami.fsra.domain.bunch_generation_context

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.FlightTaskContext
import com.wintelia.fuookami.fsra.domain.rule_context.RuleContext
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.model.*
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.service.*

class BunchGenerationContext(
    private val flightTaskContext: FlightTaskContext,
    private val ruleContext: RuleContext
) {
    lateinit var aggregation: Aggregation
    lateinit var feasibilityJudger: FlightTaskFeasibilityJudger
    lateinit var generators: Map<Aircraft, FlightTaskBunchGenerator>
    val initialFlightBunches get() = aggregation.initialFlightBunches

    fun init(aircrafts: List<Aircraft>, flightTasks: List<FlightTask>): Try<Error> {
        val flightTaskAggregation = flightTaskContext.aggregation
        val ruleAggregation = ruleContext.aggregation

        val initializer = AggregationInitializer()
        aggregation = when (val ret = initializer(aircrafts, flightTaskAggregation.aircraftUsability, flightTasks, flightTaskAggregation.originBunches, ruleAggregation.lock, feasibilityJudger, initialFlightTaskBunchGenerator)) {
            is Ok -> { ret.value }
            is Failed -> { return Failed(ret.error) }
        }


        return Ok(success)
    }
}
