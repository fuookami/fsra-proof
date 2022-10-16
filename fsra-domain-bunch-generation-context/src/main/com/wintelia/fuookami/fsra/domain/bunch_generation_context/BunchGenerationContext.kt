package com.wintelia.fuookami.fsra.domain.bunch_generation_context

import kotlin.time.*
import kotlinx.datetime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.apache.logging.log4j.kotlin.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.parallel.*
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
    private val logger = logger()

    private lateinit var aggregation: Aggregation
    private lateinit var feasibilityJudger: FlightTaskFeasibilityJudger
    private lateinit var generators: Map<Aircraft, FlightTaskBunchGenerator>
    val initialFlightBunches get() = aggregation.initialFlightBunches

    fun init(aircrafts: List<Aircraft>, flightTasks: List<FlightTask>): Try<Error> {
        val flightTaskAggregation = flightTaskContext.aggregation
        val ruleAggregation = ruleContext.aggregation

        feasibilityJudger = FlightTaskFeasibilityJudger(
            aircraftUsability = flightTaskAggregation.aircraftUsability,
            connectionTimeCalculator = ruleContext::connectionTime,
            ruleChecker = ruleContext::feasible
        )
        val initialFlightTaskBunchGenerator = InitialFlightTaskBunchGenerator(
            feasibilityJudger = feasibilityJudger,
            connectionTimeCalculator = ruleContext::connectionTime,
            minimumDepartureTimeCalculator = ruleContext::minimumDepartureTime,
            costCalculator = ruleContext::cost
        )
        val initializer = AggregationInitializer()
        aggregation = when (val ret = initializer(
            aircrafts,
            flightTaskAggregation.aircraftUsability,
            flightTasks,
            flightTaskAggregation.originBunches,
            ruleAggregation.lock,
            feasibilityJudger,
            initialFlightTaskBunchGenerator
        )) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }

        val generators = HashMap<Aircraft, FlightTaskBunchGenerator>()
        for (aircraft in aircrafts) {
            generators[aircraft] = FlightTaskBunchGenerator(
                aircraft = aircraft,
                aircraftUsability = flightTaskAggregation.aircraftUsability[aircraft]!!,
                graph = aggregation.graphs[aircraft]!!,
                connectionTimeCalculator = ruleContext::connectionTime,
                minimumDepartureTimeCalculator = ruleContext::minimumDepartureTime,
                costCalculator = ruleContext::cost,
                totalCostCalculator = ruleContext::cost
            )
        }
        this.generators = generators

        return Ok(success)
    }

    private data class Promise(
        val aircraft: Aircraft,
        val beginTime: Instant,
        val promise: ChannelGuard<Result<List<FlightTaskBunch>, Error>>
    )

    @OptIn(DelicateCoroutinesApi::class)
    fun generateFlightTaskBunch(aircrafts: List<Aircraft>, iteration: UInt64, shadowPriceMap: ShadowPriceMap): Result<List<FlightTaskBunch>, Error> {
        val promises = ArrayList<Promise>()

        for (aircraft in aircrafts) {
            val promise = Channel<Result<List<FlightTaskBunch>, Error>>()
            GlobalScope.launch {
                promise.send(generators[aircraft]!!(iteration, shadowPriceMap))
                logger.debug { "Sub-problem of ${aircraft.regNo} started." }
            }
            promises.add(
                Promise(
                    aircraft = aircraft,
                    beginTime = Clock.System.now(),
                    promise = ChannelGuard(promise)
                )
            )
        }

        val bunches = ArrayList<FlightTaskBunch>()
        for (promise in promises) {
            val thisBunches = when (val ret = runBlocking { promise.promise.receive() }) {
                is Ok -> {
                    ret.value
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
            val amount = thisBunches.size
            val duration = Clock.System.now() - promise.beginTime
            bunches.addAll(thisBunches)

            logger.debug { "Sub-problem of ${promise.aircraft.regNo} finished: ${duration.toInt(DurationUnit.MILLISECONDS)} ms， bunches amount: $amount." }
        }
        return Ok(bunches)
    }
}