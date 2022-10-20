package com.wintelia.fuookami.fsra.domain.rule_context.service

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.*

class FlightTaskFeasibilityJudger(
    val aggregation: Aggregation
) {
    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        val subprocess = listOf(
            // FlightTaskFeasibilityJudger::checkRestrictions,
            // FlightTaskFeasibilityJudger::checkLinks,
            FlightTaskFeasibilityJudger::checkAdditionalFlights,
            FlightTaskFeasibilityJudger::checkTransferFlights,
            FlightTaskFeasibilityJudger::checkCancelLockFlights,
            FlightTaskFeasibilityJudger::checkEnabledAircrafts,
        )

        for (process in subprocess) {
            if (!process(this, aircraft, prevFlightTask, flightTask)) {
                return false
            }
        }
        return true
    }

    private fun checkRestrictions(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        if (flightTask.strongLimitIgnored) {
            return true
        }

        for (restriction in aggregation.restrictions(aircraft)) {
            if (!restriction.check(flightTask, aircraft)) {
                return false
            }
        }
        return true
    }

    private fun checkLinks(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        if (prevFlightTask == null) {
            return true
        }
        return !aggregation.linkMap.isStopover(prevFlightTask, flightTask)
    }

    private fun checkAdditionalFlights(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        // todo: if implement additional flight
        return true
    }

    private fun checkTransferFlights(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        if (prevFlightTask == null || flightTask.type !is TransferFlightFlightTask) {
            return true
        }
        if (!aggregation.enabledAircrafts.contains(aircraft)
            || !(flightTask as TransferFlight).plan.enabled(aircraft)
        ) {
            return false
        }
        return true
    }

    private fun checkCancelLockFlights(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        return !aggregation.lock.lockedCancelFlightTasks.contains(flightTask.key)
    }

    private fun checkEnabledAircrafts(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        return aggregation.enabledAircrafts.contains(aircraft)
                || (flightTask.aircraft?.let { aggregation.enabledAircrafts.contains(it) } ?: true)
    }
}
