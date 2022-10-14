package com.wintelia.fuookami.fsra.domain.rule_context.service

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.*

class FlightTaskFeasibilityJudger(
    val aggregation: Aggregation
) {
    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, FlightTask: FlightTask): Boolean {
        // todo
        return true
    }

    fun checkRestrictions(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        // todo
        return true
    }

    fun checkLinks(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        // todo
        return true
    }

    fun checkAdditionalFlights(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        // todo
        return true
    }

    fun checkTransferFlights(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        // todo
        return true
    }

    fun checkCancelLockFlights(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        // todo
        return true
    }

    fun checkEnabledAircrafts(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        // todo
        return true
    }
}
