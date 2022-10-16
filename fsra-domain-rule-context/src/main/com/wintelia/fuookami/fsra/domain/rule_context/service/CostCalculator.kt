package com.wintelia.fuookami.fsra.domain.rule_context.service

import kotlinx.datetime.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class CostCalculator(
    val aggregation: Aggregation,
    val connectionTimeCalculator: ConnectionTimeCalculator,
    val minimumDepartureTimeCalculator: MinimumDepartureTimeCalculator,
    val aircraftUsability: Map<Aircraft, AircraftUsability>,
    val flightLinks: FlightLinkMap,
    val parameter: Parameter
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

    fun overMaxDelayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun aircraftChangeCost(flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun airportChangeCost(flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun restrictionCost(flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun lockCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun transferCost(flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun aircraftUsabilityCost(flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun airportConnectionCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun timeConnectionCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun connectionBreakCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun linkBreakCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun orderChangeCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem? {
        // todo
        return null
    }

    fun overFlightHourCost(aircraft: Aircraft, time: Instant, flightHour: FlightHour): CostItem? {
        // todo
        return null
    }

    fun additionalOverFlightHourCost(aircraft: Aircraft, time: Instant, flightHour: FlightHour, additionalFlightHour: FlightHour): CostItem? {
        // todo
        return null
    }

    fun overFlightCycleCost(aircraft: Aircraft, time: Instant, flightHour: FlightCycle): CostItem? {
        // todo
        return null
    }

    fun additionalOverFlightCycleCost(aircraft: Aircraft, time: Instant, flightCycle: FlightCycle, additionalOverFlightCycleCost: FlightCycle): CostItem? {
        // todo
        return null
    }
}
