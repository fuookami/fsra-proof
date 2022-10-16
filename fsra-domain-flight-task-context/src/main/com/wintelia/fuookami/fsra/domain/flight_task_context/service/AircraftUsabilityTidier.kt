package com.wintelia.fuookami.fsra.domain.flight_task_context.service

import org.apache.logging.log4j.kotlin.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class AircraftUsabilityTidier {
    val logger = logger()

    operator fun invoke(
        aircrafts: List<Aircraft>,
        flightTasks: List<FlightTask>,
        aircraftDTOList: List<AircraftDTO>,
        recoveryPlan: RecoveryPlan
    ): Result<Map<Aircraft, AircraftUsability>, Error> {
        val map = HashMap<Aircraft, AircraftUsability>()
        when (val ret = findLastFlightTask(map, flightTasks, recoveryPlan)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret = complete(map, aircrafts, aircraftDTOList, recoveryPlan)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        for ((aircraft, usability) in map) {
            logger.info { "Aircraft ${aircraft.regNo} could be used at ${usability.location}, ${usability.enabledTime}" }
        }
        return Ok(map)
    }

    private fun findLastFlightTask(map: MutableMap<Aircraft, AircraftUsability>, flightTasks: List<FlightTask>, recoveryPlan: RecoveryPlan): Try<Error> {
        val aogTimes = HashMap<Aircraft, TimeRange>()
        flightTasks.asSequence()
            .filter { it.type is AOGFlightTask }
            .forEach { aogTimes[it.aircraft!!] = it.scheduledTime!! }

        // possibly is the last flight of a aircraft before time window
        val lastFlights = flightTasks.asSequence()
            .filter {
                val time = it.time ?: return@filter false
                if (time.begin >= recoveryPlan.timeWindow.end) {
                    return@filter false
                }
                if (!it.recoveryNeeded(recoveryPlan.timeWindow)) {
                    return@filter true
                } else if (!it.recoveryNeeded(recoveryPlan.recoveryTime)) {
                    if (it.type is AOGFlightTask) {
                        return@filter true
                    } else {
                        val aircraft = it.aircraft ?: return@filter false
                        val scheduledTime = it.scheduledTime ?: return@filter false
                        if (aogTimes[aircraft]?.contains(scheduledTime.begin) == true) {
                            return@filter true
                        }
                    }
                }
                return@filter false
            }

        // bucket sort find the last flight of each aircraft
        for (flightTask in flightTasks) {
            val aircraft = flightTask.aircraft!!
            val enabledTime = flightTask.time!!.end + flightTask.connectionTime(aircraft, null)

            if (!map.containsKey(aircraft)
                || map[aircraft]!!.enabledTime < enabledTime
            ) {
                map[aircraft] = AircraftUsability(
                    lastTask = flightTask,
                    location = flightTask.arr,
                    enabledTime = enabledTime,
                )
            }
        }
        return Ok(success)
    }

    private fun complete(map: MutableMap<Aircraft, AircraftUsability>, aircrafts: List<Aircraft>, aircraftDTOList: List<AircraftDTO>, recoveryPlan: RecoveryPlan): Try<Error> {
        for (aircraft in aircrafts) {
            if (map.containsKey(aircraft)) {
                continue
            }

            val dto = aircraftDTOList.find { it.regNo == aircraft.regNo }
            val icao = dto?.endAirport
                ?: return Failed(Err(ErrorCode.ApplicationError, "Could not find initialization airport of ${aircraft.regNo}."))
            val airport = Airport(icao)
            if (airport == null) {
                logger.warn { "Found unknown airport with icao: ${icao}." }
                continue
            }
            map[aircraft] = AircraftUsability(
                lastTask = null,
                location = airport,
                enabledTime = parseDateTime(dto.enabledTime)
            )
        }

        return Ok(success)
    }
}
