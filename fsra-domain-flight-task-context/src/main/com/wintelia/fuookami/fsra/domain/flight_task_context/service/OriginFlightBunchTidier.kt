package com.wintelia.fuookami.fsra.domain.flight_task_context.service

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class OriginFlightBunchTidier {
    operator fun invoke(aircraftUsability: Map<Aircraft, AircraftUsability>, flightTasks: List<FlightTask>, timeWindow: TimeRange): Result<List<FlightTaskBunch>, Error> {
        val flightTaskMap = HashMap<Aircraft, MutableList<FlightTask>>()
        for (flightTask in flightTasks.asSequence().filter { it.recoveryNeeded(timeWindow) }) {
            val aircraft = flightTask.aircraft ?: continue
            if (!flightTaskMap.containsKey(aircraft)) {
                flightTaskMap[aircraft] = ArrayList()
            }
            flightTaskMap[aircraft]!!.add(flightTask)
        }

        val flightBunches = ArrayList<FlightTaskBunch>()
        for ((aircraft, thisFlightTasks) in flightTaskMap) {
            if (flightTasks.isEmpty() || !aircraftUsability.containsKey(aircraft)) {
                continue
            }

            flightBunches.add(FlightTaskBunch(
                aircraft = aircraft,
                ability = aircraftUsability[aircraft]!!,
                flightTasks = thisFlightTasks
                    .filter { it.scheduledTime != null }
                    .sortedBy { it.scheduledTime!!.begin },
                iteration = FlightTaskBunch.originIteration,
            )
            )
        }
        return Ok(flightBunches)
    }
}
