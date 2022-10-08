package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.concept.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*

class FlightTaskBunch(
    val aircraft: Aircraft,
    val time: TimeRange,
    val dep: Airport,
    val arr: Airport,
    val flightTasks: List<FlightTask>,
    val iteration: UInt64,
    val cost: Cost,

    val ability: AircraftUsability
) : ManualIndexed() {
    val size get() = flightTasks.size
    val empty get() = flightTasks.isEmpty()
    val lastTask by ability::lastTask
    val busyTime: Duration
    val totalDelay: Duration
    val keys: Map<FlightTaskKey, Int>
    val redundancy: Map<FlightTaskKey, Pair<Duration, Duration>>

    companion object {
        val originIteration = UInt64.maximum
    }

    constructor(
        aircraft: Aircraft,
        airport: Airport,
        time: Instant,
        ability: AircraftUsability,
        iteration: UInt64
    ) : this(
        aircraft = aircraft,
        time = TimeRange(time, time + 1.minutes),
        dep = airport,
        arr = airport,
        flightTasks = emptyList(),
        iteration = iteration,
        cost = Cost(),
        ability = ability
    )

    constructor(
        aircraft: Aircraft,
        ability: AircraftUsability,
        flightTasks: List<FlightTask>,
        iteration: UInt64,
        cost: Cost = Cost()
    ) : this(
        aircraft = aircraft,
        time = TimeRange(flightTasks.first().time!!.begin, flightTasks.last().time!!.end),
        dep = flightTasks.first().dep,
        arr = flightTasks.last().arr,
        flightTasks = flightTasks,
        iteration = iteration,
        cost = cost,
        ability = ability
    )

    init {
        val flightTaskKeys = HashMap<FlightTaskKey, Int>()
        for ((index, flight) in flightTasks.withIndex()) {
            flightTaskKeys[flight.key] = index
        }
        keys = flightTaskKeys

        val flightTaskTimeRedundancy = HashMap<FlightTaskKey, Pair<Duration, Duration>>()
        if (flightTasks.isNotEmpty()) {
            var currentFlightTaskTimeWindow = Pair(ability.enabledTime, flightTasks[0].latestNormalStartTime(aircraft))
            for (flightTask in flightTasks) {

            }
        }
        redundancy = flightTaskTimeRedundancy

        var busyTime = 0L.minutes
        for (i in flightTasks.indices) {
            busyTime += flightTasks[i].duration!!

            // var prevTask = if (i > 0) { flightTasks[ i - 1 ] } else { null }
            val nextTask = if (i != (flightTasks.size - 1)) {
                flightTasks[i + 1]
            } else {
                null
            }

            busyTime += flightTasks[i].connectionTime(aircraft, nextTask)
        }
        this.busyTime = busyTime

        totalDelay = flightTasks.sumOf { it.delay.toLong(DurationUnit.MINUTES) }.minutes
    }

    operator fun get(index: Int): FlightTask {
        return flightTasks[index]
    }

    fun contains(task: FlightTask): Boolean {
        return keys.contains(task.key)
    }

    fun contains(taskPair: Pair<FlightTask, FlightTask>): Boolean {
        val prevTask = keys[taskPair.first.key]
        val nextTask = keys[taskPair.second.key]
        return if (prevTask != null && nextTask != null) {
            (nextTask - prevTask) == 1
        } else {
            false
        }
    }

    fun get(originTask: FlightTask): FlightTask? {
        val task = keys[originTask.key]
        return if (task != null) {
            assert(flightTasks[task].originTask == originTask)
            flightTasks[task]
        } else {
            null
        }
    }

    fun arrivedWhen(airport: Airport, timeWindow: TimeRange): Boolean {
        if (!time.withIntersection(timeWindow)) {
            return false
        }

        for (task in flightTasks) {
            if (task.arrivedWhen(airport, timeWindow)) {
                return true
            }
            if (task.time!!.end >= timeWindow.end) {
                break
            }
        }
        return false
    }

    fun departedWhen(airport: Airport, timeWindow: TimeRange): Boolean {
        if (!time.withIntersection(timeWindow)) {
            return false
        }

        for (task in flightTasks) {
            if (task.departedWhen(airport, timeWindow)) {
                return true
            }
            if (task.time!!.end >= timeWindow.end) {
                break
            }
        }
        return false
    }

    fun locatedWhen(airport: Airport, timeWindow: TimeRange): Boolean {
        if (flightTasks.first().departedWhen(airport, timeWindow)) {
            return true
        }
        if (flightTasks.last().arrivedWhen(airport, timeWindow)) {
            return true
        }
        if (!empty) {
            for (i in 1 until flightTasks.size) {
                if (flightTasks[i].locatedWhen(flightTasks[i - 1], airport, timeWindow)) {
                    return true
                }
            }
        }
        return false
    }
}
