package com.wintelia.fuookami.fsra.domain.rule_context.service

import kotlin.time.*
import kotlinx.datetime.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class MinimumDepartureTimeCalculator(
    lock: Lock,
    flowControls: List<FlowControl>
) {
    private val departureCloses: Map<Airport, List<FlowControl>>
    private val arrivalCloses: Map<Airport, List<FlowControl>>
    private val lockedTime: Map<FlightTaskKey, Instant>

    init {
        val departureCloses = HashMap<Airport, ArrayList<FlowControl>>()
        val arrivalCloses = HashMap<Airport, ArrayList<FlowControl>>()
        for (flowControl in flowControls) {
            if (flowControl.capacity.closed) {
                val airport = flowControl.airport
                when (flowControl.scene) {
                    FlowControlScene.Departure -> {
                        if (departureCloses.containsKey(airport)) {
                            departureCloses[airport] = ArrayList()
                        }
                        departureCloses[airport]!!.add(flowControl)
                    }
                    FlowControlScene.Arrival, FlowControlScene.Stay -> {
                        if (arrivalCloses.containsKey(airport)) {
                            arrivalCloses[airport] = ArrayList()
                        }
                        arrivalCloses[airport]!!.add(flowControl)
                    }
                    FlowControlScene.DepartureArrival -> {
                        if (departureCloses.containsKey(airport)) {
                            departureCloses[airport] = ArrayList()
                        }
                        departureCloses[airport]!!.add(flowControl)
                        if (arrivalCloses.containsKey(airport)) {
                            arrivalCloses[airport] = ArrayList()
                        }
                        arrivalCloses[airport]!!.add(flowControl)
                    }
                }
            }
        }
        for ((_, closes) in departureCloses) {
            closes.sortBy { it.time.begin }
        }
        for ((_, closes) in arrivalCloses) {
            closes.sortBy { it.time.begin }
        }

        this.departureCloses = departureCloses
        this.arrivalCloses = arrivalCloses

        val lockedTime = HashMap<FlightTaskKey, Instant>()
        for (recoveryLock in lock.recoveryLocks) {
            recoveryLock.value.lockedTime?.let { lockedTime[recoveryLock.key] = it }
        }
        this.lockedTime = lockedTime
    }

    operator fun invoke(lastArrivalTime: Instant, aircraft: Aircraft, flightTask: FlightTask, connectionTime: Duration): Instant {
        val minimumDepartureTime = lastArrivalTime + connectionTime
        val originDepartureTime = flightTask.time?.begin ?: flightTask.timeWindow!!.begin
        var estimatedDepartureTime = maxOf(minimumDepartureTime, originDepartureTime)
        val key = flightTask.key
        if (lockedTime.contains(key)) {
            return maxOf(minimumDepartureTime, lockedTime[key]!!)
        }
        if (!flightTask.isFlight) {
            return estimatedDepartureTime
        }

        val duration = flightTask.duration(aircraft)
        var estimatedArrivalTime = estimatedDepartureTime + duration
        while (true) {
            var flag = true
            if (departureCloses.contains(flightTask.dep)) {
                val flowControls = departureCloses[flightTask.dep]!!
                for (flowControl in flowControls) {
                    if (flowControl.time.contains(estimatedDepartureTime)) {
                        estimatedDepartureTime = maxOf(estimatedDepartureTime, flowControl.time.end)
                        estimatedArrivalTime = estimatedDepartureTime + duration
                        flag = false
                    }
                }
            }
            if (arrivalCloses.contains(flightTask.arr)) {
                val flowControls = arrivalCloses[flightTask.arr]!!
                for (flowControl in flowControls) {
                    if (flowControl.time.contains(estimatedArrivalTime)) {
                        estimatedArrivalTime = maxOf(estimatedArrivalTime, flowControl.time.end)
                        estimatedDepartureTime = estimatedArrivalTime - duration
                        flag = false
                    }
                }
            }
            if (flag) {
                break
            }
        }
        return estimatedDepartureTime
    }
}
