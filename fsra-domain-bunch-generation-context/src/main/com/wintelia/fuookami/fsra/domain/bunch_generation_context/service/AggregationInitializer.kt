package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import java.lang.Exception
import kotlinx.datetime.*
import kotlin.time.Duration.*
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.apache.logging.log4j.kotlin.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.concept.*
import fuookami.ospf.kotlin.utils.parallel.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.*
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.model.*
import com.wintelia.fuookami.fsra.infrastructure.Configuration
import kotlin.time.Duration

class AggregationInitializer {
    private val logger = logger()

    operator fun invoke(
        aircrafts: List<Aircraft>,
        aircraftUsability: Map<Aircraft, AircraftUsability>,
        flightTasks: List<FlightTask>,
        originBunches: List<FlightTaskBunch>,
        lock: Lock,
        flightTaskFeasibilityJudger: FlightTaskFeasibilityJudger,
        initialFlightTaskBunchGenerator: InitialFlightTaskBunchGenerator,
        configuration: Configuration,
    ): Result<Aggregation, Error> {
        val reverse = when (val ret = initReverseEnabledFlight(flightTasks, originBunches, lock, configuration)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        val flightTaskGroups = groupFlightTasks(flightTasks)
        val graphGenerator = RouteGraphGenerator(reverse, configuration) { aircraft: Aircraft, prevFlightTask: FlightTask?, succFlightTask: FlightTask ->
            flightTaskFeasibilityJudger(aircraft, prevFlightTask, succFlightTask)
        }
        val graphs = when (val ret = if (configuration.multiThread) {
            generateGraphMultiThread(aircrafts, aircraftUsability, flightTaskGroups, graphGenerator)
        } else {
            generateGraphSingleThread(aircrafts, aircraftUsability, flightTaskGroups, graphGenerator)
        }) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }

        val initialFlightBunches = when (val ret = generateInitialFlightTaskBunches(aircrafts, aircraftUsability, flightTasks, originBunches, initialFlightTaskBunchGenerator)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }


        return Ok(
            Aggregation(
                graphs = graphs,
                reverse = reverse,
                initialFlightBunches = initialFlightBunches
            )
        )
    }

    private fun groupFlightTasks(flightTasks: List<FlightTask>): Map<Airport, List<FlightTask>> {
        val flightTaskGroups = HashMap<Airport, MutableList<FlightTask>>()
        for (flightTask in flightTasks) {
            if (!flightTaskGroups.containsKey(flightTask.dep)) {
                flightTaskGroups[flightTask.dep] = ArrayList()
            }
            flightTaskGroups[flightTask.dep]!!.add(flightTask)

            for (dep in flightTask.depBackup) {
                if (!flightTaskGroups.containsKey(dep)) {
                    flightTaskGroups[dep] = ArrayList()
                }
                flightTaskGroups[flightTask.dep]!!.add(flightTask)
            }
        }
        for ((_, thisFlightTasks) in flightTaskGroups) {
            thisFlightTasks.sortBy { it.scheduledTime?.begin ?: it.timeWindow?.begin ?: Instant.DISTANT_FUTURE }
        }
        return flightTaskGroups
    }

    private fun initReverseEnabledFlight(
        flightTasks: List<FlightTask>,
        originBunches: List<FlightTaskBunch>,
        lock: Lock,
        configuration: Configuration
    ): Result<FlightTaskReverse, Error> {
        var timeDifferenceLimit = FlightTaskReverse.defaultTimeDifferenceLimit
        val pairs = ArrayList<Pair<FlightTask, FlightTask>>()
        if (configuration.withOrderChange && flightTasks.size >= 2) {
            while (true) {
                pairs.clear()
                for (i in flightTasks.indices) {
                    for (j in (i + 1) until flightTasks.size) {
                        if (FlightTaskReverse.symmetrical(flightTasks[i], flightTasks[j], lock, timeDifferenceLimit + (FlightTaskReverse.defaultTimeDifferenceLimit / 2.0))) {
                            pairs.add(Pair(flightTasks[i], flightTasks[j]))
                        } else {
                            if (FlightTaskReverse.reverseEnabled(flightTasks[i], flightTasks[j], lock, timeDifferenceLimit)) {
                                pairs.add(Pair(flightTasks[i], flightTasks[j]))
                            }
                            if (FlightTaskReverse.reverseEnabled(flightTasks[j], flightTasks[i], lock, timeDifferenceLimit)) {
                                pairs.add(Pair(flightTasks[j], flightTasks[i]))
                            }
                        }
                    }
                }

                if (timeDifferenceLimit == 0.hours) {
                    break
                } else if (pairs.size <= FlightTaskReverse.criticalSize.toInt() && timeDifferenceLimit <= 3.hours) {
                    break
                } else {
                    timeDifferenceLimit -= 1.hours
                }
            }
        }
        return Ok(FlightTaskReverse(pairs, originBunches, lock, timeDifferenceLimit))
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun generateGraphMultiThread(
        aircrafts: List<Aircraft>,
        aircraftUsability: Map<Aircraft, AircraftUsability>,
        flightTaskGroups: Map<Airport, List<FlightTask>>,
        graphGenerator: RouteGraphGenerator
    ): Result<Map<Aircraft, Graph>, Error> {
        val promises = ArrayList<Pair<Aircraft, ChannelGuard<Result<Graph, Error>>>>()

        for (aircraft in aircrafts) {
            val promise = Channel<Result<Graph, Error>>()
            GlobalScope.launch {
                try {
                    promise.send(graphGenerator(aircraft, aircraftUsability[aircraft]!!, flightTaskGroups))
                } catch (e: Exception) {
                    logger.warn { "$e" }
                }
            }
            promises.add(Pair(aircraft, ChannelGuard(promise)))
        }
        val graphs = HashMap<Aircraft, Graph>()
        for ((aircraft, promise) in promises) {
            when (val ret = runBlocking { promise.receive() }) {
                is Ok -> {
                    graphs[aircraft] = ret.value
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        return Ok(graphs)
    }

    private fun generateGraphSingleThread(
        aircrafts: List<Aircraft>,
        aircraftUsability: Map<Aircraft, AircraftUsability>,
        flightTaskGroups: Map<Airport, List<FlightTask>>,
        graphGenerator: RouteGraphGenerator
    ): Result<Map<Aircraft, Graph>, Error> {
        val graphs = HashMap<Aircraft, Graph>()
        for (aircraft in aircrafts) {
            when (val ret = graphGenerator(aircraft, aircraftUsability[aircraft]!!, flightTaskGroups)) {
                is Ok -> {
                    graphs[aircraft] = ret.value
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        return Ok(graphs)
    }

    private fun generateInitialFlightTaskBunches(
        aircrafts: List<Aircraft>,
        aircraftUsability: Map<Aircraft, AircraftUsability>,
        flightTasks: List<FlightTask>,
        originBunches: List<FlightTaskBunch>,
        generator: InitialFlightTaskBunchGenerator
    ): Result<List<FlightTaskBunch>, Error> {
        val generatedAircraft = HashSet<Aircraft>()
        val bunches = ArrayList<FlightTaskBunch>()

        for (bunch in originBunches) {
            if (!aircrafts.contains(bunch.aircraft)) {
                continue
            }

            val aircraft = bunch.aircraft
            val lockedFlightTasks = flightTasks.filter { isLocked(it, bunch.aircraft) }.sortedBy { it.time?.begin }
            val newBunch = generator(aircraft, aircraftUsability[bunch.aircraft]!!, lockedFlightTasks, bunch) ?: continue
            generatedAircraft.add(bunch.aircraft)
            bunches.add(newBunch)

            logger.info { "Initial bunch of ${aircraft.regNo} generated: ${newBunch.size} tasks, origin: ${bunch.size} tasks."}
        }

        for (aircraft in aircrafts) {
            if (generatedAircraft.contains(aircraft)) {
                continue
            }

            val bunch = originBunches.find { it.aircraft == aircraft }
            val lockedFlightTasks = flightTasks.filter { isLocked(it, aircraft) }.sortedBy { it.time?.begin }
            val newBunch = generator.emptyBunch(aircraft, aircraftUsability[aircraft]!!, lockedFlightTasks)
            if (newBunch != null) {
                bunches.add(newBunch)
                logger.info { "Initial bunch of ${aircraft.regNo} generated: ${newBunch.size} tasks, origin: ${bunch?.size ?: 0} tasks."}
            } else {
                logger.info { "Failed to generate initial bunch of ${aircraft.regNo}, origin: ${bunch?.size ?: 0} tasks. "}
            }
        }

        ManualIndexed.flush<FlightTaskBunch>()
        for (bunch in bunches) {
            bunch.setIndexed()
        }

        return Ok(bunches)
    }

    private fun isLocked(flightTask: FlightTask, aircraft: Aircraft): Boolean {
        return !flightTask.cancelEnabled && !flightTask.aircraftChangeEnabled && flightTask.aircraft == aircraft
    }
}
