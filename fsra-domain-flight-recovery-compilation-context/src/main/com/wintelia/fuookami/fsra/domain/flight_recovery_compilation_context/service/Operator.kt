package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

typealias AircraftChecker = (Aircraft) -> Boolean
typealias CancelCostCalculator = Extractor<Flt64, FlightTask>
typealias DelayCostCalculator = (FlightTask?, FlightTask) -> Flt64
