package com.wintelia.fuookami.fsra.domain.bunch_generation_context

import kotlin.time.*
import kotlinx.datetime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.apache.logging.log4j.kotlin.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.concept.*
import fuookami.ospf.kotlin.utils.parallel.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.FlightTaskContext
import com.wintelia.fuookami.fsra.domain.rule_context.RuleContext
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.PassengerContext
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.service.*

class BunchGenerationContext(
    private val flightTaskContext: FlightTaskContext,
    private val ruleContext: RuleContext,
    private val passengerContext: PassengerContext
) {
    private val logger = logger()

    private lateinit var aggregation: Aggregation
    private lateinit var feasibilityJudger: FlightTaskFeasibilityJudger
    private lateinit var generators: Map<Aircraft, FlightTaskBunchGenerator>
    private lateinit var costCalculator: CostCalculator
    private lateinit var totalCostCalculator: TotalCostCalculator
    val initialFlightBunches get() = aggregation.initialFlightBunches

    fun init(aircrafts: List<Aircraft>, flightTasks: List<FlightTask>, configuration: Configuration): Try<Error> {
        val flightTaskAggregation = flightTaskContext.aggregation
        val ruleAggregation = ruleContext.aggregation

        feasibilityJudger = FlightTaskFeasibilityJudger(
            aircraftUsability = flightTaskAggregation.aircraftUsability,
            connectionTimeCalculator = ruleContext::connectionTime,
            ruleChecker = ruleContext::feasible
        )
        costCalculator = if (configuration.withPassenger) {
            { aircraft: Aircraft, prevTask: FlightTask?, task: FlightTask, flightHour: FlightHour, flightCycle: FlightCycle ->
                val cost1 = ruleContext.cost(aircraft, prevTask, task, flightHour, flightCycle)
                val cost2 = passengerContext.cost(aircraft, prevTask, task)
                if (cost1 != null && cost2 != null) {
                    cost1 += cost2
                    cost1
                } else {
                    null
                }
            }
        } else {
            { aircraft: Aircraft, prevTask: FlightTask?, task: FlightTask, flightHour: FlightHour, flightCycle: FlightCycle ->
                ruleContext.cost(aircraft, prevTask, task, flightHour, flightCycle)
            }
        }
        totalCostCalculator = if (configuration.withPassenger) {
            { aircraft: Aircraft, tasks: List<FlightTask> ->
                val cost1 = ruleContext.cost(aircraft, tasks)
                val cost2 = passengerContext.cost(aircraft, tasks)
                if (cost1 != null && cost2 != null) {
                    cost1 += cost2
                    cost1
                } else {
                    null
                }
            }
        } else {
            { aircraft: Aircraft, tasks: List<FlightTask> ->
                ruleContext.cost(aircraft, tasks)
            }
        }
        val initialFlightTaskBunchGenerator = InitialFlightTaskBunchGenerator(
            feasibilityJudger = feasibilityJudger,
            connectionTimeCalculator = ruleContext::connectionTime,
            minimumDepartureTimeCalculator = ruleContext::minimumDepartureTime,
            costCalculator = totalCostCalculator
        )
        val initializer = AggregationInitializer()
        aggregation = when (val ret = initializer(
            aircrafts,
            flightTaskAggregation.aircraftUsability,
            flightTasks,
            flightTaskAggregation.originBunches,
            ruleAggregation.lock,
            feasibilityJudger,
            initialFlightTaskBunchGenerator,
            configuration
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
                costCalculator = costCalculator,
                totalCostCalculator = totalCostCalculator,
                configuration
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

    fun generateFlightTaskBunch(aircrafts: List<Aircraft>, iteration: UInt64, shadowPriceMap: ShadowPriceMap, configuration: Configuration): Result<List<FlightTaskBunch>, Error> {
        return if (configuration.multiThread) {
            generateFlightTaskBunchMultiThread(aircrafts, iteration, shadowPriceMap)
        } else {
            generateFlightTaskBunchSingleThread(aircrafts, iteration, shadowPriceMap)
        }
    }

    private fun generateFlightTaskBunchSingleThread(aircrafts: List<Aircraft>, iteration: UInt64, shadowPriceMap: ShadowPriceMap): Result<List<FlightTaskBunch>, Error> {
        val bunches = ArrayList<FlightTaskBunch>()
        for (aircraft in aircrafts) {
            // logger.debug { "Sub-problem of ${aircraft.regNo} started." }
            val beginTime = Clock.System.now()
            val thisBunches = when (val ret = generators[aircraft]!!(iteration, shadowPriceMap)) {
                is Ok -> {
                    ret.value
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
            val amount = thisBunches.size
            val duration = Clock.System.now() - beginTime
            bunches.addAll(thisBunches)
            logger.debug { "Sub-problem of ${aircraft.regNo} finished: ${duration.toInt(DurationUnit.MILLISECONDS)} ms, bunches amount: $amount." }
        }

        ManualIndexed.flush<FlightTaskBunch>()
        for (bunch in bunches) {
            bunch.setIndexed()
        }

        return Ok(bunches)
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun generateFlightTaskBunchMultiThread(aircrafts: List<Aircraft>, iteration: UInt64, shadowPriceMap: ShadowPriceMap): Result<List<FlightTaskBunch>, Error> {
        val promises = ArrayList<Promise>()

        for (aircraft in aircrafts) {
            val promise = Channel<Result<List<FlightTaskBunch>, Error>>()
            GlobalScope.launch {
                promise.send(generators[aircraft]!!(iteration, shadowPriceMap))
                // logger.debug { "Sub-problem of ${aircraft.regNo} started." }
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

            logger.debug { "Sub-problem of ${promise.aircraft.regNo} finished: ${duration.toInt(DurationUnit.MILLISECONDS)} ms, bunches amount: $amount." }
        }

        ManualIndexed.flush<FlightTaskBunch>()
        for (bunch in bunches) {
            bunch.setIndexed()
        }

        return Ok(bunches)
    }
}
