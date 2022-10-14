package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import kotlin.time.*
import kotlinx.datetime.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.infrastructure.*

typealias RuleChecker = (Aircraft, FlightTask?, FlightTask) -> Boolean
typealias ConnectionTimeCalculator = (Aircraft, FlightTask, FlightTask?) -> Duration
typealias MinimumDepartureTimeCalculator = (Instant, Aircraft, FlightTask, Duration) -> Instant
typealias CostCalculator = (Aircraft, FlightTask?, FlightTask, FlightHour, FlightCycle) -> Cost?
typealias TotalCostCalculator = (Aircraft, List<FlightTask>) -> Cost?
