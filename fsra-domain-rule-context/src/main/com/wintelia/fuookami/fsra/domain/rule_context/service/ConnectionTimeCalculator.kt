package com.wintelia.fuookami.fsra.domain.rule_context.service

import kotlin.time.*
import kotlinx.datetime.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class ConnectionTimeCalculator(
    private val flightLinkMap: FlightLinkMap
) {
    operator fun invoke(flightTask: FlightTask, succFlightTask: FlightTask?): Duration? {
        return if (succFlightTask == null) {
            Duration.ZERO
        } else {
            val connectionTimeIfStopover = flightLinkMap.connectionTimeIfStopover(flightTask, succFlightTask)
            if (connectionTimeIfStopover != null) {
                return connectionTimeIfStopover
            }
            if (flightLinkMap.isConnectionTimeIgnoring(flightTask, succFlightTask) || succFlightTask.strongLimitIgnored) {
                val standardConnectionTime = flightTask.connectionTime(succFlightTask)!!
                connectionTime(flightTask, succFlightTask, standardConnectionTime)
            } else {
                flightTask.connectionTime(succFlightTask)
            }
        }
    }

    operator fun invoke(aircraft: Aircraft, flightTask: FlightTask, succFlightTask: FlightTask?): Duration {
        if (succFlightTask == null) {
            return Duration.ZERO
        } else {
            val connectionTimeIfStopover = flightLinkMap.connectionTimeIfStopover(flightTask, succFlightTask)
            if (connectionTimeIfStopover != null) {
                return connectionTimeIfStopover
            }
            if (flightLinkMap.isConnectionTimeIgnoring(flightTask, succFlightTask) || succFlightTask.strongLimitIgnored) {
                val standardConnectionTime = flightTask.connectionTime(succFlightTask)!!
                connectionTime(flightTask, succFlightTask, standardConnectionTime)
            } else {
                flightTask.connectionTime(aircraft, succFlightTask)
            }
        }
        return Duration.ZERO
    }

    private fun connectionTime(flightTask: FlightTask, succFlightTask: FlightTask, standardConnectionTime: Duration): Duration {
        val connectionTime = maxOf(Duration.ZERO, (succFlightTask.time?.begin ?: Instant.DISTANT_PAST) - flightTask.time!!.end)
        return minOf(standardConnectionTime, connectionTime)
    }
}
