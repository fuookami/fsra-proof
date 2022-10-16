package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class InitialFlightTaskBunchGenerator(
    val feasibilityJudger: FlightTaskFeasibilityJudger,
    val connectionTimeCalculator: ConnectionTimeCalculator,
    val minimumDepartureTimeCalculator: MinimumDepartureTimeCalculator,
    val costCalculator: CostCalculator
) {
    operator fun invoke(aircraftUsability: AircraftUsability, lockedFlightTasks: List<FlightTask>, originBunch: FlightTaskBunch): FlightTaskBunch? {
        // todo
        return null
    }

    fun emptyBunch(aircraft: Aircraft, aircraftUsability: AircraftUsability, lockedFlightTasks: List<FlightTask>): FlightTaskBunch? {
        // todo
        return null
    }

    private fun softRecovery(originBunch: FlightTaskBunch, aircraftUsability: AircraftUsability, lockedFlightTasks: List<FlightTask>): FlightTaskBunch? {
        // todo
        return null
    }

    private fun recoveryFlightTasks(aircraft: Aircraft, aircraftUsability: AircraftUsability, lockedFlightTasks: List<FlightTask>): List<FlightTask> {
        // todo
        return emptyList()
    }

    private fun feasible(lastFlight: FlightTask?, bunch: FlightTaskBunch): Boolean {
        // todo
        return true
    }
}
