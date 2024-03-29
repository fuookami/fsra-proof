package com.wintelia.fuookami.fsra.domain.flight_task_context

import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class Aggregation(
    val airports: List<Airport>,
    val aircrafts: List<Aircraft>,
    val aircraftUsability: Map<Aircraft, AircraftUsability>,

    val flights: List<Flight>,
    val maintenances: List<Maintenance>,
    val aogs: List<AOG>,
    val transferFlights: List<TransferFlight>,

    val originBunches: List<FlightTaskBunch>
) {
    val flightTasks: List<FlightTask>

    init {
        val flightTasks = ArrayList<FlightTask>()
        flightTasks.addAll(flights)
        flightTasks.addAll(maintenances)
        flightTasks.addAll(aogs)
        flightTasks.addAll(transferFlights)
        this.flightTasks = flightTasks
    }

    fun enabled(aircraft: Aircraft, recoveryPlan: RecoveryPlan): Boolean {
        return aircraftUsability[aircraft]?.enabledTime?.let { it < recoveryPlan.recoveryTime.end } ?: false
    }
}
