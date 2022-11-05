package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import fuookami.ospf.kotlin.framework.model.invoke
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.FlightTaskContext
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.RuleContext
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.limits.FlightTaskConnectionTimeLimit

class FlightRecoveryCompilationContext(
    private val flightTaskContext: FlightTaskContext,
    private val ruleContext: RuleContext
) {
    lateinit var aggregation: Aggregation
    private lateinit var pipelineList: MutableList<CGPipeline<LinearMetaModel, ShadowPriceMap>>

    val recoveryNeededAircrafts get() = aggregation.recoveryNeededAircrafts
    val recoveryNeededFlightTasks get() = aggregation.recoveryNeededFlightTasks

    val columnAmount get() = UInt64((aggregation.bunches.size - aggregation.removedBunches.size).toULong())

    fun init(recoveryPlan: RecoveryPlan, configuration: Configuration): Try<Error> {
        val flightTaskAggregation = flightTaskContext.aggregation
        val ruleAggregation = ruleContext.aggregation

        val flightTasks = ArrayList<FlightTask>()
        flightTasks.addAll(flightTaskAggregation.flightTasks)
        flightTasks.addAll(ruleAggregation.flightTasks)

        val initializer = AggregationInitializer()
        aggregation = when (val ret = initializer(
            flightTaskAggregation.aircrafts,
            flightTasks,
            flightTaskAggregation.aircraftUsability,
            flightTaskAggregation.originBunches,
            ruleAggregation.linkMap,
            ruleAggregation.flowControls,
            {
                flightTaskAggregation.enabled(it, recoveryPlan)
                        && ruleAggregation.enabled(it)
                        && !(configuration.onlyWithPassenger && it.capacity is AircraftCapacity.Cargo)
                        && !(configuration.onlyWithCargo && it.capacity is AircraftCapacity.Passenger)
            },
            recoveryPlan,
            configuration
        )) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }

        return Ok(success)
    }

    fun register(model: LinearMetaModel, configuration: Configuration): Try<Error> {
        assert(this::aggregation.isInitialized)
        val ruleAggregation = ruleContext.aggregation
        return aggregation.register(ruleAggregation.lock, model, configuration)
    }

    fun construct(recoveryPlan: RecoveryPlan, model: LinearMetaModel, configuration: Configuration, parameter: Parameter, cancelCostCalculator: CancelCostCalculator? = null): Try<Error> {
        assert(this::aggregation.isInitialized)
        if (!this::pipelineList.isInitialized) {
            val ruleAggregation = ruleContext.aggregation

            val generator = PipelineListGenerator(aggregation)
            pipelineList = when (val ret = generator(
                {  flightTask: FlightTask ->
                    var cost = ruleContext.cancelCost(flightTask).value ?: Flt64.infinity
                    cancelCostCalculator?.let { calculator -> cost += calculator(flightTask) }
                    cost
                },
                { prevFlightTask, flightTask -> ruleContext.delayCost(prevFlightTask, flightTask).value ?: Flt64.infinity },
                ruleAggregation.linkMap,
                recoveryPlan,
                configuration,
                parameter
            )) {
                is Ok -> {
                    ret.value.toMutableList()
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        return when (val ret = pipelineList(model)) {
            is Ok -> {
                Ok(success)
            }

            is Failed -> {
                Failed(ret.error)
            }
        }
    }

    fun analyzeSolution(recoveryPlan: RecoveryPlan, iteration: UInt64, model: LinearMetaModel): Result<OutputAnalyzer.Output, Error> {
        val analyzer = OutputAnalyzer(aggregation)
        return analyzer(recoveryPlan, iteration, model)
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

    fun extractFixedBunches(iteration: UInt64, model: LinearMetaModel): Result<Set<FlightTaskBunch>, Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.extractFixedBunches(iteration, model)
    }

    fun extractKeptFlightBunches(iteration: UInt64, model: LinearMetaModel): Result<Set<FlightTaskBunch>, Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.extractKeptFlightBunches(iteration, model)
    }

    fun extractHiddenAircrafts(model: LinearMetaModel): Result<Set<Aircraft>, Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.extractHiddenAircrafts(model)
    }

    fun selectFreeAircrafts(
        fixedBunches: Set<FlightTaskBunch>,
        hiddenAircrafts: Set<Aircraft>,
        shadowPriceMap: ShadowPriceMap,
        model: LinearMetaModel,
        configuration: Configuration
    ): Result<Set<Aircraft>, Error> {
        assert(this::aggregation.isInitialized)
        val selector = FreeAircraftSelector(aggregation, configuration.freeAircraftSelectorConfiguration)
        return selector(fixedBunches, hiddenAircrafts, shadowPriceMap, model)
    }

    fun globallyFix(fixedBunches: Set<FlightTaskBunch>): Try<Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.globallyFix(fixedBunches)
    }

    fun locallyFix(iteration: UInt64, bar: Flt64, fixedBunches: Set<FlightTaskBunch>, model: LinearMetaModel): Result<Set<FlightTaskBunch>, Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.locallyFix(iteration, bar, fixedBunches, model)
    }

    fun logResult(iteration: UInt64, model: LinearMetaModel): Try<Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.logResult(iteration, model)
    }

    fun logBunchCost(iteration: UInt64, model: LinearMetaModel): Try<Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.logBunchCost(iteration, model)
    }

    fun flush(iteration: UInt64): Try<Error> {
        assert(this::aggregation.isInitialized)
        val ruleAggregation = ruleContext.aggregation
        return aggregation.flush(iteration, ruleAggregation.lock)
    }

    fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>, recoveryPlan: RecoveryPlan, model: LinearMetaModel, configuration: Configuration): Try<Error> {
        assert(this::aggregation.isInitialized)
        when (val ret = aggregation.addColumns(iteration, bunches, recoveryPlan.timeWindow, model, configuration)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }

        if (configuration.withRedundancy) {
            val newPipeline = FlightTaskConnectionTimeLimit(iteration, bunches, aggregation.compilation, aggregation.flightTaskTime, recoveryPlan.timeWindow)
            when (val ret = newPipeline(model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
            pipelineList.add(newPipeline)
        }

        return Ok(success)
    }

    fun removeColumns(
        maximumReducedCost: Flt64,
        maximumColumnAmount: UInt64,
        shadowPriceMap: ShadowPriceMap,
        fixedBunches: Set<FlightTaskBunch>,
        keptBunches: Set<FlightTaskBunch>,
        model: LinearMetaModel
    ): Result<Flt64, Error> {
        assert(this::aggregation.isInitialized)
        return aggregation.removeColumns(maximumReducedCost, maximumColumnAmount, shadowPriceMap, fixedBunches, keptBunches, model)
    }
}
