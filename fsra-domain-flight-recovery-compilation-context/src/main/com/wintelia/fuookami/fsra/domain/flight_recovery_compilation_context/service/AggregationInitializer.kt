package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.concept.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class AggregationInitializer {
    operator fun invoke(
        aircrafts: List<Aircraft>,
        flightTasks: List<FlightTask>,
        aircraftUsability: Map<Aircraft, AircraftUsability>,
        originBunches: List<FlightTaskBunch>,
        flightLinkMap: FlightLinkMap,
        flowControls: List<FlowControl>,
        aircraftChecker: AircraftChecker,
        recoveryPlan: RecoveryPlan,
        configuration: Configuration
    ): Result<Aggregation, Error> {
        val recoveryNeededAircrafts = ArrayList<Aircraft>()

        for (aircraft in aircrafts) {
            if (aircraftChecker(aircraft)) {
                recoveryNeededAircrafts.add(aircraft)
            }
        }

        val aogTimes = HashMap<Aircraft, TimeRange>()
        for (task in flightTasks) {
            if (task.type is AOGFlightTask) {
                aogTimes[task.aircraft!!] = task.scheduledTime!!
            }
        }

        val recoveryNeededFlightTasks = ArrayList<FlightTask>()
        for (flightTask in flightTasks) {
            if (flightTask.aircraft?.let { !aircraftChecker(it) } == true) {
                continue
            }

            if (flightTask.recoveryNeeded(recoveryPlan.recoveryTime)) {
                recoveryNeededFlightTasks.add(flightTask)
                continue
            }

            val aircraft = flightTask.aircraft ?: continue
            val time = flightTask.time ?: continue
            if (flightTask.recoveryNeeded(recoveryPlan.timeWindow)
                && flightTask.type !is AOGFlightTask
            ) {
                val aogTime = aogTimes[aircraft] ?: continue
                if (aogTime.contains(time.begin)) {
                    recoveryNeededFlightTasks.add(flightTask)
                    continue
                }
            }
        }

        ManualIndexed.flush<Aircraft>()
        for (aircraft in recoveryNeededAircrafts) {
            aircraft.setIndexed()
        }

        ManualIndexed.flush<FlightTask>()
        for (flightTask in recoveryNeededFlightTasks) {
            flightTask.setIndexed(FlightTask::class)
        }

        return Ok(
            Aggregation(
                recoveryNeededAircrafts = recoveryNeededAircrafts,
                recoveryNeededFlightTasks = recoveryNeededFlightTasks,
                aircraftUsability = aircraftUsability,
                originBunches = originBunches,
                flightLinkMap = flightLinkMap,
                flowControls = flowControls,
                recoveryPlan = recoveryPlan,
                configuration = configuration
            )
        )
    }
}
