package com.wintelia.fuookami.fsra.domain.rule_context.service

import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class CostCalculator(
    val aggregation: Aggregation,
    val connectionTimeCalculator: ConnectionTimeCalculator,
    val minimumDepartureTimeCalculator: MinimumDepartureTimeCalculator,
    val aircraftUsability: Map<Aircraft, AircraftUsability>,
    val parameter: Parameter,
    val flightLinks: FlightLinkMap
) {
    operator fun invoke(bunch: FlightTaskBunch): Cost? {
        // todo
        return null
    }

    operator fun invoke(aircraft: Aircraft, flightTasks: List<FlightTask>): Cost? {
        // todo
        return null
    }

    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Cost? {
        // todo
        return null
    }

    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask, prevFlightHour: FlightHour, prevFlightCycle: FlightCycle): Cost? {
        // todo
        return null
    }

    fun cancelCost(flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun delayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem? {
        // todo
        return null
    }
}
