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
    fun cancelCost(flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        if (flightTask.type is FlightFlightTask
            && !lockedCancelFlightTasks.contains(flightTask.originTask)
        ) {
            passengerGroup[flightTask]?.forEach {
                cost += flightTask.weight * parameter.passengerCancel * it.passenger.num.toFlt64()
            }
        }
        return CostItem("passenger cancel", cost)
    }

    fun delayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        val delay = flightTask.actualDelay
        if (flightTask.type is FlightFlightTask
            && delay != Duration.ZERO
            // todo: check if it is not locked with time
        ) {
            val delayHours = Flt64(delay.toDouble(DurationUnit.HOURS))
            passengerGroup[flightTask]?.forEach {
                cost += flightTask.weight * if (delayHours leq Flt64.one) {
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
}
