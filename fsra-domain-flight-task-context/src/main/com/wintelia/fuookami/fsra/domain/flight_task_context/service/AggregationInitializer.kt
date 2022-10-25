package com.wintelia.fuookami.fsra.domain.flight_task_context.service

import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import org.apache.logging.log4j.kotlin.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.PassengerClass

class AggregationInitializer {
    private val logger = logger()

    operator fun invoke(input: Input): Result<Aggregation, Error> {
        val airports = when (val result = initAirports(input.airports)) {
            is Ok -> {
                result.value
            }

            is Failed -> {
                return Failed(result.error)
            }
        }
        val aircrafts = when (val result = initAircrafts(
            input.aircrafts,
            input.aircraftTypes,
            input.aircraftFee,
            input.aircraftMinorTypeRouteFlyTime,
            input.aircraftMinorTypeConnectionTime,
            input.flights
        )) {
            is Ok -> {
                result.value
            }

            is Failed -> {
                return Failed(result.error)
            }
        }
        val flights = when (val result = initFlights(input.flights)) {
            is Ok -> {
                result.value
            }

            is Failed -> {
                return Failed(result.error)
            }
        }
        val maintenances = when (val result = initMaintenances(input.lineMaintenances, input.scheduleMaintenances, input.plan)) {
            is Ok -> {
                result.value
            }

            is Failed -> {
                return Failed(result.error)
            }
        }
        val aogs = when (val result = initAOG(input.aogs)) {
            is Ok -> {
                result.value
            }

            is Failed -> {
                return Failed(result.error)
            }
        }
        val transferFlights = when (val result = initTransferFlight(input.transferFlights)) {
            is Ok -> {
                result.value
            }

            is Failed -> {
                return Failed(result.error)
            }
        }
        val flightTasks = ArrayList<FlightTask>()
        flightTasks.addAll(flights)
        flightTasks.addAll(maintenances)
        flightTasks.addAll(aogs)
        flightTasks.addAll(transferFlights)
        val aircraftUsability =
            when (val result = initAircraftUsability(aircrafts, flightTasks, input.aircrafts, input.plan)) {
                is Ok -> {
                    result.value
                }

                is Failed -> {
                    return Failed(result.error)
                }
            }
        val originBunches =
            when (val result = initOriginFlightTaskBunch(aircraftUsability, flightTasks, input.plan)) {
                is Ok -> {
                    result.value
                }

                is Failed -> {
                    return Failed(result.error)
                }
            }

        return Ok(
            Aggregation(
                airports = airports,
                aircrafts = aircrafts,
                aircraftUsability = aircraftUsability,
                flights = flights,
                maintenances = maintenances,
                aogs = aogs,
                transferFlights = transferFlights,
                originBunches = originBunches
            )
        )
    }

    private fun initAirports(airportDTOList: List<AirportDTO>): Result<List<Airport>, Error> {
        val airports = ArrayList<Airport>()
        for (airportDTO in airportDTOList) {
            val type = AirportType(airportDTO.type)
            if (type == null) {
                logger.warn { "Found unknown airport type: ${airportDTO.type}." }
                continue
            }
            airports.add(
                Airport(
                    icao = airportDTO.icao,
                    type = type
                )
            )
        }
        return Ok(airports)
    }

    private fun initAircrafts(
        aircraftDTOList: List<AircraftDTO>,
        aircraftTypeDTOList: List<AircraftTypeDTO>,
        aircraftFeeDTOList: List<AircraftFeeDTO>,
        aircraftRouteFlyTimeDTOList: List<AircraftMinorTypeRouteFlyTimeDTO>,
        aircraftConnectionTimeDTOList: List<AircraftMinorTypeConnectionTimeDTO>,
        flightDTOList: List<FlightDTO>
    ): Result<List<Aircraft>, Error> {
        val aircraftTypes = HashMap<AircraftTypeCode, AircraftType>()
        val aircraftMinorTypes = HashMap<AircraftMinorTypeCode, AircraftMinorType>()
        for (type in aircraftTypeDTOList) {
            aircraftTypes[type.type] = AircraftType(type.type)
            val fee = aircraftFeeDTOList.find { it.minorType == type.minorType }
            if (fee == null) {
                logger.warn { "Could not find fee of aircraft minor type: ${type.minorType}." }
                continue
            }
            val routeFlyTime = HashMap<Route, Duration>()
            for (routeFlyTimeDTO in aircraftRouteFlyTimeDTOList.asIterable().filter { it.minorType == type.minorType }) {
                val dep = Airport(routeFlyTimeDTO.dep)
                if (dep == null) {
                    logger.warn { "Found unknown airport with icao: ${routeFlyTimeDTO.dep}." }
                    continue
                }

                val arr = Airport(routeFlyTimeDTO.arr)
                if (arr == null) {
                    logger.warn { "Found unknown airport with icao: ${routeFlyTimeDTO.arr}." }
                    continue
                }

                routeFlyTime[Route(dep, arr)] = routeFlyTimeDTO.routeFlyTime.toInt().minutes
            }
            if (routeFlyTime.isEmpty()) {
                logger.warn { "There are no route fly time for ${type.minorType}, it will be read from flights." }

                for (flightDTO in flightDTOList.asIterable().filter { it.acType == type.minorType }) {
                    val dep = Airport(flightDTO.dep)
                    if (dep == null) {
                        logger.warn { "Found unknown airport with icao: ${flightDTO.dep}." }
                        continue
                    }

                    val arr = Airport(flightDTO.arr)
                    if (arr == null) {
                        logger.warn { "Found unknown airport with icao: ${flightDTO.arr}." }
                        continue
                    }

                    val duration = flightDTO.sta - flightDTO.std
                    val route = Route(dep, arr)
                    routeFlyTime[route] = routeFlyTime[route]?.let { maxOf(it, duration) } ?: duration
                }
            }
            val connectionTime = HashMap<Airport, Duration>()
            for (connectionTimeDTO in aircraftConnectionTimeDTOList.asIterable().filter { it.minorType == type.minorType }) {
                val airport = Airport(connectionTimeDTO.airport)
                if (airport == null) {
                    logger.warn { "Found unknown airport with icao: ${connectionTimeDTO.airport}." }
                    continue
                }

                connectionTime[airport] = connectionTimeDTO.connectionTime.toInt().minutes
            }
            if (connectionTime.isEmpty()) {
                logger.error { "There are no connection time for ${type.minorType}." }
                return Failed(Err(ErrorCode.ApplicationError, "No connection time for ${type.minorType}"))
            }
            aircraftMinorTypes[type.minorType] = AircraftMinorType(
                type = aircraftTypes[type.type]!!,
                code = type.minorType,
                costPerHour = fee.costPerHour,
                routeFlyTime = routeFlyTime,
                connectionTime = connectionTime
            )
        }
        val aircrafts = ArrayList<Aircraft>()
        for (aircraftDTO in aircraftDTOList) {
            val capacity = when (aircraftDTO.passengerCargoType) {
                "passenger" -> {
                    AircraftCapacity.Passenger(
                        mapOf(
                            Pair(PassengerClass.First, aircraftDTO.firstClassNum),
                            Pair(PassengerClass.Business, aircraftDTO.businessClassNum),
                            Pair(PassengerClass.Economy, aircraftDTO.economyClassNum)
                        )
                    )
                }

                "cargo" -> {
                    AircraftCapacity.Cargo(Flt64.infinity)
                }

                else -> {
                    logger.warn { "Found unknown passenger cargo type \"${aircraftDTO.passengerCargoType}\" in ${aircraftDTO.regNo}." }
                    continue
                }
            }
            val minorType = aircraftMinorTypes[aircraftDTO.minorType]
            if (minorType == null) {
                logger.warn { "Found unknown aircraft minor type \"${aircraftDTO.minorType}\" in ${aircraftDTO.regNo}." }
                continue
            }

            aircrafts.add(
                Aircraft(
                    regNo = aircraftDTO.regNo,
                    minorType = minorType,
                    capacity = capacity
                )
            )
        }
        return Ok(aircrafts)
    }

    private fun initFlights(flightDTOList: List<FlightDTO>): Result<List<Flight>, Error> {
        val flights = ArrayList<Flight>()
        for (flightDTO in flightDTOList) {
            val aircraft = Aircraft(flightDTO.acReg)
            if (aircraft == null) {
                logger.warn { "Found unknown aircraft: ${flightDTO.acReg}." }
                continue
            }
            val dep = Airport(flightDTO.dep)
            if (dep == null) {
                logger.warn { "Found unknown airport with icao: ${flightDTO.dep}." }
                continue
            }
            val arr = Airport(flightDTO.arr)
            if (arr == null) {
                logger.warn { "Found unknown airport with icao: ${flightDTO.arr}." }
                continue
            }
            val type = FlightType(flightDTO.region)
            if (type == null) {
                logger.warn { "Found unknown flight type: ${flightDTO.region}." }
                continue
            }
            val std = flightDTO.std
            val sta = flightDTO.sta
            val etd = flightDTO.etd
            val eta = flightDTO.eta
            val status = hashSetOf(
                FlightTaskStatus.NotTerminalChange
            )
            if (!flightDTO.advanceEnabled) {
                status.add(FlightTaskStatus.NotAdvance)
            }
            if (!flightDTO.delayEnabled) {
                status.add(FlightTaskStatus.NotDelay)
            }
            if (!flightDTO.cancelEnabled) {
                status.add(FlightTaskStatus.NotCancel)
            }
            if (!flightDTO.aircraftTypeChangeEnabled) {
                status.add(FlightTaskStatus.NotAircraftTypeChange)
            }
            if (!flightDTO.aircraftMinorTypeChangeEnabled) {
                status.add(FlightTaskStatus.NotAircraftMinorTypeChange)
            }
            if (!flightDTO.aircraftChangeEnabled) {
                status.add(FlightTaskStatus.NotAircraftChange)
            }
            flights.add(
                Flight(
                    FlightPlan(
                        actualId = flightDTO.id,
                        date = flightDTO.date,
                        no = flightDTO.no,
                        type = type,
                        aircraft = aircraft,
                        dep = dep,
                        arr = arr,
                        scheduledTime = TimeRange(std, sta),
                        estimatedTime = if (etd != null && eta != null) { TimeRange(etd, eta) } else { null },
                        actualTime = null,
                        outTime = null,
                        status = status,
                        weight = flightDTO.weight
                    )
                )
            )
        }
        return Ok(flights)
    }

    private fun initMaintenances(
        lineMaintenanceDTOList: List<LineMaintenanceDTO>,
        scheduleMaintenanceDTOList: List<ScheduleMaintenanceDTO>,
        recoveryPlan: RecoveryPlan,
    ): Result<List<Maintenance>, Error> {
        val maintenances = ArrayList<Maintenance>()
        for (lineMaintenanceDTO in lineMaintenanceDTOList) {
            val aircraft = Aircraft(lineMaintenanceDTO.acReg)
            if (aircraft == null) {
                logger.warn { "Found unknown aircraft: ${lineMaintenanceDTO.acReg}." }
                continue
            }
            val airports = lineMaintenanceDTO.airports.mapNotNull {
                val airport = Airport(it)
                if (airport == null) {
                    logger.warn { "Found unknown airport with icao: $it." }
                }
                airport
            }
            val begin = lineMaintenanceDTO.beginTime
            val end = lineMaintenanceDTO.endTime
            if (airports.size == 1) {
                maintenances.add(
                    Maintenance(
                        MaintenancePlan(
                            aircraft = aircraft,
                            scheduledTime = TimeRange(begin, end),
                            airports = airports,
                            expirationTime = recoveryPlan.timeWindow.end,
                            category = MaintenanceCategory.Line,
                            timeWindow = recoveryPlan.timeWindow
                        )
                    )
                )
            }
        }
        for (scheduleMaintenanceDTO in scheduleMaintenanceDTOList) {
            val aircraft = Aircraft(scheduleMaintenanceDTO.acReg)
            if (aircraft == null) {
                logger.warn { "Found unknown aircraft: ${scheduleMaintenanceDTO.acReg}." }
                continue
            }
            val airports = scheduleMaintenanceDTO.airports.mapNotNull {
                val airport = Airport(it)
                if (airport == null) {
                    logger.warn { "Found unknown airport with icao: $it." }
                }
                airport
            }
            val begin = scheduleMaintenanceDTO.beginTime
            val end = scheduleMaintenanceDTO.endTime
            if (airports.size == 1) {
                maintenances.add(
                    Maintenance(
                        MaintenancePlan(
                            aircraft = aircraft,
                            scheduledTime = TimeRange(begin, end),
                            airports = airports,
                            expirationTime = recoveryPlan.timeWindow.end,
                            category = MaintenanceCategory.Schedule,
                            timeWindow = recoveryPlan.timeWindow
                        )
                    )
                )
            }
        }
        return Ok(maintenances)
    }

    private fun initAOG(aogDTOList: List<AOGDTO>): Result<List<AOG>, Error> {
        val aogs = ArrayList<AOG>()
        for (aogDTO in aogDTOList) {
            val aircraft = Aircraft(aogDTO.acReg)
            if (aircraft == null) {
                logger.warn { "Found unknown aircraft: ${aogDTO.acReg}." }
                continue
            }
            val airports = aogDTO.airports.mapNotNull {
                val airport = Airport(it)
                if (airport == null) {
                    logger.warn { "Found unknown airport with icao: $it." }
                }
                airport
            }
            val begin = aogDTO.beginTime
            val end = aogDTO.endTime
            aogs.add(
                AOG(
                    AOGPlan(
                        aircraft = aircraft,
                        airports = airports,
                        scheduledTime = TimeRange(begin, end)
                    )
                )
            )
        }
        return Ok(aogs)
    }

    private fun initTransferFlight(transferFlightDTOList: List<TransferFlightDTO>): Result<List<TransferFlight>, Error> {
        val transferFlights = ArrayList<TransferFlight>()
        for (transferFlightDTO in transferFlightDTOList) {
            val dep = Airport(transferFlightDTO.dep)
            if (dep == null) {
                logger.warn { "Found unknown airport with icao: ${transferFlightDTO.dep}." }
                continue
            }
            val arr = Airport(transferFlightDTO.arr)
            if (arr == null) {
                logger.warn { "Found unknown airport with icao: ${transferFlightDTO.arr}." }
                continue
            }
            val begin = transferFlightDTO.beginTime
            val end = transferFlightDTO.endTime
            transferFlights.add(
                TransferFlight(
                    Transfer(
                        dep = dep,
                        arr = arr,
                        timeWindow = TimeRange(begin, end),
                        duration = transferFlightDTO.flyTime.toInt().minutes
                    )
                )
            )
        }
        return Ok(transferFlights)
    }

    private fun initAircraftUsability(
        aircrafts: List<Aircraft>,
        flightTasks: List<FlightTask>,
        aircraftDTOList: List<AircraftDTO>,
        recoveryPlan: RecoveryPlan
    ): Result<Map<Aircraft, AircraftUsability>, Error> {
        val tidier = AircraftUsabilityTidier()
        return tidier(aircrafts, flightTasks, aircraftDTOList, recoveryPlan)
    }

    private fun initOriginFlightTaskBunch(
        aircraftUsability: Map<Aircraft, AircraftUsability>,
        flightTasks: List<FlightTask>,
        recoveryPlan: RecoveryPlan
    ): Result<List<FlightTaskBunch>, Error> {
        val tidier = OriginFlightBunchTidier()
        return tidier(aircraftUsability, flightTasks, recoveryPlan.timeWindow)
    }
}
