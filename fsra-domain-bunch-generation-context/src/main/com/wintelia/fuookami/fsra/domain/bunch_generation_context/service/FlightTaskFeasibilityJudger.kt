package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class FlightTaskFeasibilityJudger(
    val aircraftUsability: Map<Aircraft, AircraftUsability>,
    val connectionTimeCalculator: ConnectionTimeCalculator,
    val ruleChecker: RuleChecker
) {
    data class Config(
        val checkEnabledTime: Boolean = false,
        val timeExtractor: Extractor<TimeRange?, FlightTask> = FlightTask::scheduledTime,
        val departureTime: Instant? = null
    )

    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask, config: Config = Config()): Boolean {
        // todo
        return true
    }

    private fun checkAircraftCapacity(aircraft: Aircraft, flightTask: FlightTask): Boolean {
        if (!flightTask.isFlight) {
            return true
        }

        return flightTask.capacity?.let { aircraft.capacity == it } != false
    }
}
