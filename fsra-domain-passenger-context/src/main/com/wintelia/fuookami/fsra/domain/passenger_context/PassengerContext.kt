package com.wintelia.fuookami.fsra.domain.passenger_context

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.invoke
import fuookami.ospf.kotlin.framework.model.CGPipelineList
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.*
import com.wintelia.fuookami.fsra.domain.passenger_context.service.*
import com.wintelia.fuookami.fsra.domain.passenger_context.service.OutputAnalyzer

class PassengerContext(
    val ruleContext: RuleContext,
    val flightRecoveryCompilationContext: FlightRecoveryCompilationContext
) {
    lateinit var aggregation: Aggregation
    private lateinit var pipelineList: CGPipelineList<LinearMetaModel, ShadowPriceMap>
    private lateinit var costCalculator: CostCalculator

    fun init(flightTasks: List<FlightTask>, input: Input, parameter: Parameter): Try<Error> {
        val initializer = AggregationInitializer()
        aggregation = when (val ret = initializer(flightTasks, input)) {
            is Ok -> { ret.value }
            is Failed -> { return Failed(ret.error) }
        }
        val ruleAggregation = ruleContext.aggregation
        costCalculator = CostCalculator(aggregation.passengerGroup, ruleAggregation.lock.lockedCancelFlightTasks, parameter)
        return Ok(success)
    }

    fun register(model: LinearMetaModel): Try<Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.register(model)
    }

    fun construct(model: LinearMetaModel, parameter: Parameter): Try<Error> {
        assert(this::aggregation.isInitialized)
        if (!this::pipelineList.isInitialized) {
            val flightRecoveryCompilationAggregation = flightRecoveryCompilationContext.aggregation

            val generator = PipelineListGenerator(aggregation)
            pipelineList = when (val ret = generator(flightRecoveryCompilationAggregation.flightCapacity, parameter)) {
                is Ok -> { ret.value }
                is Failed -> { return Failed(ret.error) }
            }
        }
        return pipelineList(model)
    }

    fun extractShadowPrice(shadowPriceMap: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        assert(this::pipelineList.isInitialized)
        for (pipeline in pipelineList) {
            when (val ret = pipeline.refresh(shadowPriceMap, model, shadowPrices)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
            val extractor = pipeline.extractor() ?: continue
            shadowPriceMap.put(extractor)
        }
        return Ok(success)
    }

    fun analyzeSolution(recoveryPlan: RecoveryPlan, recoveryedFlights: List<Pair<RecoveryedFlightDTO, FlightTask?>>, iteration: UInt64, model: LinearMetaModel): Result<OutputAnalyzer.Output, Error> {
        val analyzer = OutputAnalyzer(aggregation)
        return analyzer(recoveryPlan, recoveryedFlights, iteration, model)
    }

    fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>): Try<Error> {
        // nothing to do
        return Ok(success)
    }

    fun cost(bunch: FlightTaskBunch): Cost? {
        return costCalculator(bunch)
    }

    fun cost(aircraft: Aircraft, flightTasks: List<FlightTask>): Cost? {
        return costCalculator(aircraft, flightTasks)
    }

    fun cost(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Cost? {
        return costCalculator(aircraft, prevFlightTask, flightTask)
    }

    fun cancelCost(flightTask: FlightTask): CostItem {
        return costCalculator.cancelCost(flightTask)
    }

    fun delayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem {
        return costCalculator.delayCost(prevFlightTask, flightTask)
    }
}
