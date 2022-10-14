package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class FlightTaskFeasibilityJudger {
    data class Config(
        val checkEnabledTime: Boolean = false,
        val timeExtractor: Extractor<TimeRange?, FlightTask> = FlightTask::scheduledTime,
        val departureTime: Instant? = null
    )

    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask, config: Config = Config()): Boolean {
        // todo
        return true
    }
}
