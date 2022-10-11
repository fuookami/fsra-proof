package com.wintelia.fuookami.fsra.domain.rule_context.service

import kotlinx.datetime.*
import kotlin.time.Duration.Companion.hours
import org.apache.logging.log4j.kotlin.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class AggregationInitializer {
    private val logger = logger()

    operator fun invoke(originFlightBunches: List<FlightTaskBunch>, input: Input, parameter: Parameter): Result<Aggregation, Error> {
        val enabledAircrafts = when (val ret = initEnabledAircrafts(input.aircrafts)) {
            is Ok -> { ret.value }
            is Failed -> { return Failed(ret.error) }
        }
        val flowControls = when (val ret = initFlowControls(input.airportCloses, input.airportFlowControls)) {
            is Ok -> { ret.value }
            is Failed -> { return Failed(ret.error) }
        }
        val generalRestrictions = when (val ret = initGeneralRestrictions(input.weakRestriction, input.strongRestrictions, parameter)) {
            is Ok -> { ret.value }
            is Failed -> { return Failed(ret.error) }
        }
        val connectingFlightPairs = when (val ret = initConnectingFlightPairs(originFlightBunches, parameter)) {
            is Ok -> { ret.value }
            is Failed -> { return Failed(ret.error) }
        }
        val stopoverFlightPairs = when (val ret = initStopoverFlightPairs(input.flights, originFlightBunches, parameter)) {
            is Ok -> { ret.value }
            is Failed -> { return Failed(ret.error) }
        }

        return Ok(Aggregation(
            enabledAircrafts = enabledAircrafts,
            flowControls = flowControls,
            relationRestrictions = emptyList(),
            generalRestrictions = generalRestrictions,
            linkMap = FlightLinkMap(connectingFlightPairs, stopoverFlightPairs, emptyList())
        ))
    }

    private fun initEnabledAircrafts(aircraftDTOList: List<AircraftDTO>): Result<Set<Aircraft>, Error> {
        val enabledAircrafts = HashSet<Aircraft>()
        for (aircraftDTO in aircraftDTOList) {
            val aircraft = Aircraft(aircraftDTO.regNo)
            if (aircraft == null) {
                logger.warn { "Found unknown aircraft: ${aircraftDTO.regNo}." }
                continue
            }

            if (aircraftDTO.enabled) {
                enabledAircrafts.add(aircraft)
            }
        }
        return Ok(enabledAircrafts)
    }

    private fun initFlowControls(airportCloseDTOList: List<AirportCloseDTO>, airportFlowControlDTOList: List<AirportFlowControlDTO>): Result<List<FlowControl>, Error> {
        val flowControls = ArrayList<FlowControl>()
        for (airportCloseDTO in airportCloseDTOList) {
            val airport = Airport(airportCloseDTO.airport)
            if (airport == null) {
                logger.warn { "Found unknown airport with icao: ${airportCloseDTO.airport}." }
                continue
            }
            val begin = LocalDate.parse(airportCloseDTO.beginDate).atTime(LocalTime.parse(airportCloseDTO.beginCloseTime)).toInstant(TimeZone.currentSystemDefault())
            val end = LocalDate.parse(airportCloseDTO.endDate).atTime(LocalTime.parse(airportCloseDTO.endCloseTime)).toInstant(TimeZone.currentSystemDefault())
            flowControls.add(FlowControl(
                airport = airport,
                time = TimeRange(begin, end),
                scene = FlowControlScene.DepartureArrival,
                capacity = FlowControlCapacity.close
            ))
        }
        for (airportFlowControlDTO in airportFlowControlDTOList) {
            val airport = Airport(airportFlowControlDTO.airport)
            if (airport == null) {
                logger.warn { "Found unknown airport with icao: ${airportFlowControlDTO.airport}." }
                continue
            }
            val scene = FlowControlScene(airportFlowControlDTO.type)
            if (scene == null) {
                logger.warn { "Found unknown flow control scene: ${airportFlowControlDTO.type}." }
                continue
            }
            val begin = Instant.parse(airportFlowControlDTO.beginTime)
            val end = Instant.parse(airportFlowControlDTO.endTime)
            flowControls.add(FlowControl(
                airport = airport,
                time = TimeRange(begin, end),
                scene = scene,
                capacity = FlowControlCapacity(airportFlowControlDTO.capacity, 1.hours)
            ))
        }
        return Ok(flowControls)
    }

    private fun initGeneralRestrictions(weakRestrictionDTOList: List<WeakRestrictionDTO>, strongRestrictionDTOList: List<StrongRestrictionDTO>, parameter: Parameter): Result<List<GeneralRestriction>, Error> {
        val restrictions = ArrayList<GeneralRestriction>()
        for (weakRestrictionDTO in weakRestrictionDTOList) {
            val dep = Airport(weakRestrictionDTO.dep)
            if (dep == null) {
                logger.warn { "Found unknown airport with icao: ${weakRestrictionDTO.dep}." }
                continue
            }
            val arr = Airport(weakRestrictionDTO.arr)
            if (arr == null) {
                logger.warn { "Found unknown airport with icao: ${weakRestrictionDTO.arr}." }
                continue
            }
            val aircraft = Aircraft(weakRestrictionDTO.acReg)
            if (aircraft == null) {
                logger.warn { "Found unknown aircraft: ${weakRestrictionDTO.acReg}." }
                continue
            }
            val condition = GeneralRestrictionCondition(
                departureAirports = setOf(dep),
                arrivalAirports = setOf(arr),
                disabledAircrafts = setOf(aircraft)
            )
            restrictions.add(GeneralRestriction(
                type = RestrictionType.Weak,
                condition = condition,
                cost = weakRestrictionDTO.weight
            ))
        }
        for (strongRestrictionDTO in strongRestrictionDTOList) {
            val dep = Airport(strongRestrictionDTO.dep)
            if (dep == null) {
                logger.warn { "Found unknown airport with icao: ${strongRestrictionDTO.dep}." }
                continue
            }
            val arr = Airport(strongRestrictionDTO.arr)
            if (arr == null) {
                logger.warn { "Found unknown airport with icao: ${strongRestrictionDTO.arr}." }
                continue
            }
            val aircraft = Aircraft(strongRestrictionDTO.acReg)
            if (aircraft == null) {
                logger.warn { "Found unknown aircraft: ${strongRestrictionDTO.acReg}." }
                continue
            }
            val condition = GeneralRestrictionCondition(
                departureAirports = setOf(dep),
                arrivalAirports = setOf(arr),
                disabledAircrafts = setOf(aircraft)
            )
            restrictions.add(GeneralRestriction(
                type = RestrictionType.ViolableStrong,
                condition = condition,
                cost = strongRestrictionDTO.weight
            ))
        }
        return Ok(restrictions)
    }

    private fun initConnectingFlightPairs(originFlightBunches: List<FlightTaskBunch>, parameter: Parameter): Result<List<ConnectingFlightPair>, Error> {
        val pairs = ArrayList<ConnectingFlightPair>()
        for (bunch in originFlightBunches) {
            for (i in 0 until (bunch.size - 1)) {
                if (bunch[i] is Flight && bunch[i + 1] is Flight) {
                    val prevFlight = bunch[i] as Flight
                    val succFlight = bunch[i + 1] as Flight
                    if (prevFlight.plan.no == succFlight.plan.no) {
                        pairs.add(ConnectingFlightPair(prevFlight, succFlight, parameter.connectingFlightSplit))
                    }
                }
            }
        }
        return Ok(pairs)
    }

    private fun initStopoverFlightPairs(flightDTOList: List<FlightDTO>, originFlightBunches: List<FlightTaskBunch>, parameter: Parameter): Result<List<StopoverFlightPair>, Error> {
        val pairs = ArrayList<StopoverFlightPair>()
        for (bunch in originFlightBunches) {
            for (i in 0 until (bunch.size - 1)) {
                if (bunch[i] is Flight && bunch[i + 1] is Flight) {
                    val prevFlight = bunch[i] as Flight
                    val succFlight = bunch[i + 1] as Flight
                    if (flightDTOList.find { it.id == prevFlight.id }?.stopoverFlight == true
                        && flightDTOList.find { it.id == succFlight.id }?.stopoverFlight == true
                    ) {
                        pairs.add(StopoverFlightPair(prevFlight, succFlight, parameter.stopoverFlightSplit))
                    }
                }
            }
        }
        return Ok(pairs)
    }
}
