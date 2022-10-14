package com.wintelia.fuookami.fsra.domain.rule_context

import kotlin.time.*
import kotlinx.datetime.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.service.*

class RuleContext(
    val flightTaskContext: FlightTaskContext
) {
    lateinit var aggregation: Aggregation
    lateinit var feasibilityJudger: FlightTaskFeasibilityJudger
    lateinit var connectionTimeCalculator: ConnectionTimeCalculator
    lateinit var minimumDepartureTimeCalculator: MinimumDepartureTimeCalculator
    lateinit var costCalculator: CostCalculator

    fun feasible(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        return feasibilityJudger(aircraft, prevFlightTask, flightTask)
    }

    fun connectionTime(aircraft: Aircraft, flightTask: FlightTask, succFlightTask: FlightTask?): Duration {
        return connectionTimeCalculator(aircraft, flightTask, succFlightTask)
    }

    fun minimumDepartureTime(lastArrivalTime: Instant, aircraft: Aircraft, flightTask: FlightTask, connectionTime: Duration): Instant {
        return minimumDepartureTimeCalculator(lastArrivalTime, aircraft, flightTask, connectionTime)
    }

    fun cost(bunch: FlightTaskBunch): Cost? {
        return costCalculator(bunch)
    }

    fun cost(aircraft: Aircraft, flightTasks: List<FlightTask>): Cost? {
        return costCalculator(aircraft, flightTasks)
    }

    fun cost(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask, prevFlightHour: FlightHour, prevFlightCycle: FlightCycle): Cost? {
        return costCalculator(aircraft, prevFlightTask, flightTask, prevFlightHour, prevFlightCycle)
    }

    fun cancelCost(flightTask: FlightTask): CostItem? {
        return costCalculator.cancelCost(flightTask)
    }

    fun delayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem? {
        return costCalculator.delayCost(prevFlightTask, flightTask)
    }
}
