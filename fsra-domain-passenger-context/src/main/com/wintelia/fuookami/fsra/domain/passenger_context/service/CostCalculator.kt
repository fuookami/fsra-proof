package com.wintelia.fuookami.fsra.domain.passenger_context.service

import kotlin.time.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.model.*

class CostCalculator(
    val passengerGroup: Map<FlightTask, List<PassengerFlight>>,
    val lockedCancelFlightTasks: Set<FlightTask>,
    val parameter: Parameter
) {
    operator fun invoke(bunch: FlightTaskBunch): Cost? {
        return this(bunch.aircraft, bunch.flightTasks, bunch.lastTask)
    }

    operator fun invoke(aircraft: Aircraft, flightTasks: List<FlightTask>, lastFightTask: FlightTask? = null): Cost? {
        val cost = Cost()
        for (i in flightTasks.indices) {
            val prevFlightTask = if (i == 0) {
                lastFightTask
            } else {
                flightTasks[i - 1]
            }

            cost += this(aircraft, prevFlightTask, flightTasks[i]) ?: return null
            if (!cost.valid) {
                return cost
            }
        }

        return cost
    }

    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Cost? {
        val calculators = arrayListOf(
            { delayCost(prevFlightTask, flightTask) },
            { aircraftChangeCost(aircraft, prevFlightTask, flightTask) }
        )

        val cost = Cost()
        for (calc in calculators) {
            cost += calc()
            if (!cost.valid) {
                return null
            }
        }
        return cost
    }

    fun cancelCost(flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        if (flightTask.type is FlightFlightTask
            && !lockedCancelFlightTasks.contains(flightTask.originTask)
        ) {
            passengerGroup[flightTask]?.forEach {
                cost += flightTask.weight * parameter.passengerCancel * it.passenger.amount.toFlt64()
            }
        }
        return CostItem("passenger cancel", cost)
    }

    fun delayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        val delay = flightTask.delay
        if (flightTask.type is FlightFlightTask
            && delay != Duration.ZERO
            // todo: check if it is not locked with time
        ) {
            val delayHours = Flt64(delay.toDouble(DurationUnit.HOURS))
            passengerGroup[flightTask]?.forEach {
                cost += it.amount.toFlt64() * flightTask.weight * if (delayHours leq Flt64.one) {
                    parameter.passengerDelay1H
                } else if (delayHours leq Flt64(4.0)) {
                    parameter.passengerDelay4H
                } else if (delayHours leq Flt64(8.0)) {
                    parameter.passengerDelay8H
                } else {
                    parameter.passengerDelayOver8H
                }
            }
        }
        return CostItem("passenger delay", cost)
    }

    fun aircraftChangeCost(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        val originTotalAmount = passengerGroup[flightTask]?.sumOf { it.amount.toInt() }?.let { UInt64(it.toULong()) } ?: UInt64.zero
        val totalAmount = when (val ret = aircraft.capacity) {
            is AircraftCapacity.Passenger -> { ret.total }
            else -> { UInt64.zero }
        }
        if (originTotalAmount > totalAmount) {
            cost += (originTotalAmount - totalAmount).toFlt64() * parameter.passengerCancel
        }
        return CostItem("passenger cancel by aircraft change", cost)
    }
}
