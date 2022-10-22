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
        val subProcesses = listOf(
            { checkAircraft(aircraft, flightTask) },
            { checkAircraftType(aircraft, flightTask) },
            { checkAircraftMinorType(aircraft, flightTask) },
            { checkAircraftCapacity(aircraft, flightTask) },
            { checkAircraftUsability(aircraft, prevFlightTask, flightTask, config) },
            { checkAirportConnection(aircraft, prevFlightTask, flightTask) },
            { checkFlyTime(aircraft, flightTask) },
            { checkTime(aircraft, prevFlightTask, flightTask, config) },
            { checkTimeWindow(aircraft, prevFlightTask, flightTask, config) },
            { checkRules(aircraft, prevFlightTask, flightTask) },
        )

        for (process in subProcesses) {
            if (!process()) {
                return false
            }
        }
        return true
    }

    private fun checkAircraft(aircraft: Aircraft, flightTask: FlightTask): Boolean {
        return flightTask.aircraftChangeEnabled || flightTask.aircraft == aircraft
    }

    private fun checkAircraftType(aircraft: Aircraft, flightTask: FlightTask): Boolean {
        return flightTask.aircraftTypeChangeEnabled || flightTask.aircraft?.type == aircraft.type
    }

    private fun checkAircraftMinorType(aircraft: Aircraft, flightTask: FlightTask): Boolean {
        return flightTask.aircraftTypeChangeEnabled || flightTask.aircraft?.minorType == aircraft.minorType
    }

    private fun checkAircraftCapacity(aircraft: Aircraft, flightTask: FlightTask): Boolean {
        return !flightTask.isFlight || flightTask.capacity?.let { it.category == aircraft.capacity.category } != false
    }

    private fun checkAircraftUsability(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask, config: Config): Boolean {
        if (prevFlightTask != null) {
            return true
        }
        if (flightTask.dep != aircraftUsability[aircraft]!!.location) {
            return false
        }
        if (config.checkEnabledTime) {
            val time = config.timeExtractor(flightTask)
            if (time != null && time.begin < aircraftUsability[aircraft]!!.enabledTime) {
                return false
            }
        }
        return true
    }

    private fun checkAirportConnection(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        if (prevFlightTask == null) {
            return true
        }
        if (prevFlightTask.arr == flightTask.dep) {
            return true
        } else {
            val arr = arrayListOf(prevFlightTask.arr)
            arr.addAll(prevFlightTask.arrBackup)

            val dep = arrayListOf(flightTask.dep)
            dep.addAll(flightTask.depBackup)

            for (airport in arr) {
                if (dep.contains(airport)) {
                    return true
                }
            }

            return false
        }
    }

    private fun checkFlyTime(aircraft: Aircraft, flightTask: FlightTask): Boolean {
        if (!flightTask.isFlight) {
            return true
        }

        val duration = flightTask.duration
        return duration == null || duration < aircraft.maxRouteFlyTime
    }

    private fun checkTime(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask, config: Config): Boolean {
        if (prevFlightTask == null) {
            return true
        }

        val prevTime = config.timeExtractor(prevFlightTask)
        val time = config.timeExtractor(flightTask)
        if (prevTime == null || time == null) {
            return true
        }

        val lastBeginTime = flightTask.plan.latestBeginTime
        if (lastBeginTime != null && time.begin > lastBeginTime) {
            return false
        }

        return prevTime.begin < time.begin
    }

    private fun checkTimeWindow(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask, config: Config): Boolean {
        if (prevFlightTask == null) {
            return true
        }

        val prevTime = config.timeExtractor(prevFlightTask)
        val time = config.timeExtractor(flightTask)

        if (prevTime != null && time != null) {
            return true
        }

        val prevTimeWindow = prevFlightTask.timeWindow
        val timeWindow = flightTask.timeWindow
        return if (prevTime != null && timeWindow != null) {
            val minimumDepartureTime = prevTime.begin + prevFlightTask.duration(aircraft) + connectionTimeCalculator(aircraft, prevFlightTask, flightTask)
            val maximumDepartureTime = timeWindow.end - flightTask.duration(aircraft)
            return minimumDepartureTime <= maximumDepartureTime
        } else if (time != null && prevTimeWindow != null) {
            prevTimeWindow.begin <= time.begin
        } else if (prevTimeWindow != null && timeWindow != null) {
            val minimumDepartureTime = prevTimeWindow.begin + prevFlightTask.duration(aircraft) + connectionTimeCalculator(aircraft, prevFlightTask, flightTask)
            val maximumDepartureTime = timeWindow.end - flightTask.duration(aircraft)
            minimumDepartureTime <= maximumDepartureTime
        } else {
            false
        }
    }

    private fun checkRules(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Boolean {
        if (prevFlightTask == null || !prevFlightTask.isFlight || !flightTask.isFlight) {
            return true
        }
        return ruleChecker(aircraft, prevFlightTask, flightTask)
    }
}
