package com.wintelia.fuookami.fsra.infrastructure

import com.wintelia.fuookami.fsra.infrastructure.dto.ParameterDTO
import fuookami.ospf.kotlin.utils.math.*

data class Parameter(
    // operation
    val overMaxDelay: Flt64 = Flt64(5000.0),
    val weakRestrictionViolation: Flt64 = Flt64(10.0),
    val strongRestrictionViolation: Flt64 = Flt64(5000.0),
    val inviolableStrongRestrictionViolation: Flt64? = Flt64(10000000.0),

    val recoveryLock: Flt64 = Flt64(10000.0),
    val recoveryLockCancel: Flt64 = Flt64(100000.0),

    val connectingFlightSplit: Flt64 = Flt64(500.0),
    val stopoverFlightSplit: Flt64 = Flt64(100000.0),
    val fleetBalanceSlack: Flt64 = Flt64(60.0),
    val fleetBalanceBaseSlack: Flt64 = Flt64(600.0),
    val flowControlSlack: Flt64 = Flt64(50000.0),

    // aircraft
    val aircraftLeisure: Flt64 = Flt64(0.0),
    val aircraftCycleNumber: Flt64 = Flt64(20000.0),
    val aircraftCycleHour: Flt64 = Flt64(20000.0),

    // flights
    val flightAdvancePerFlight: Flt64 = Flt64(100.0),
    val flightDelayPerHour: Flt64 = Flt64(250.0),
    val flightDelay1H: Flt64 = Flt64(100.0),
    val flightDelay4H: Flt64 = Flt64(200.0),
    val flightDelay8H: Flt64 = Flt64(300.0),
    val flightDelayOver8H: Flt64 = Flt64(400.0),
    val flightCancel: Flt64 = Flt64(9999.0),
    val outOfOrder: Flt64 = Flt64(0.0),

    val keyFlightCancel: Flt64 = Flt64(100000.0),
    val keyFlightDelay: Flt64 = Flt64(100000.0),
    // val keyFlightAircraftChange: Flt64 = Flt64(100000.0),
    // val keyFlightAircraftTypeChange: Flt64 = Flt64(100000.0),

    val aircraftChange: Flt64 = Flt64(20.0),
    val aircraftTypeChangeBase: Flt64 = Flt64(100.0),
    val aircraftTypeChange: List<Triple<AircraftMinorTypeCode, AircraftMinorTypeCode, Flt64>> = emptyList(),
    val aircraftChangeIntraDay: Flt64 = Flt64(1.0),
    val aircraftChangeNextDay: Flt64 = Flt64(0.5),
    val aircraftChangeTertianDay: Flt64 = Flt64(0.5),

    // maintenance
    val maintenanceDelay: Flt64 = Flt64(10000.0),
    val maintenanceCancel: Flt64 = Flt64(10000.0),
    val maintenanceAirportChange: Flt64 = Flt64(75.0),

    // AOG
    val AOGCancel: Flt64 = Flt64(600000.0),
    val AOGDelay: Flt64 = Flt64(600000.0),

    // transfer flight
    val transferFlight: Flt64 = Flt64(1200.0),

    // straight flight
    val straightFlight: Flt64 = Flt64(200.0),

    // additional flight
    val additionalFlightCancel: Flt64 = Flt64(1000000.0),
    val additionalFlightDelay: Flt64 = Flt64(1000000.0),

    // passenger
    val passengerDelay1H: Flt64 = Flt64(1.0),
    val passengerDelay4H: Flt64 = Flt64(2.0),
    val passengerDelay8H: Flt64 = Flt64(3.0),
    val passengerDelayOver8H: Flt64 = Flt64(4.0),
    val passengerCancel: Flt64 = Flt64(3.0),
    val passengerConnectionSplit: Flt64 = Flt64(10.0),

    val passengerClassChangeBase: Flt64 = Flt64(0.0),
    val passengerClassChange: List<Triple<PassengerClass, PassengerClass, Flt64>> = emptyList()
) {
    constructor(parameter: Map<String, ParameterDTO>) : this(
        weakRestrictionViolation = parameter["COST_VIOLATE_WEAK_LIMIT"]!!.value,
        connectingFlightSplit = parameter["COST_FLIGHT_CONNECT_SPLIT"]!!.value,
        fleetBalanceSlack = parameter["COST_END_AIRCRAFT_UNBALANCED"]!!.value,
        fleetBalanceBaseSlack = parameter["COST_COST_END_UNBALANCED"]!!.value,

        flightAdvancePerFlight = parameter["COST_FLIGHT_AHEAD"]!!.value,
        flightDelayPerHour = parameter["COST_FLIGHT_DELAY_HOUR"]!!.value,
        flightDelay1H = parameter["COST_FLIGHT_DELAY_1_HOUR"]!!.value,
        flightDelay4H = parameter["COST_FLIGHT_DELAY_1_TO_4_HOUR"]!!.value,
        flightDelay8H = parameter["COST_FLIGHT_DELAY_4_TO_8_HOUR"]!!.value,
        flightDelayOver8H = parameter["COST_FLIGHT_DELAY_8_HOUR"]!!.value,
        flightCancel = parameter["COST_FLIGHT_CANCEL"]!!.value,
        outOfOrder = parameter["COST_FLIGHT_ADJACENT_SPLIT"]!!.value,

        aircraftChange = parameter["COST_CHANGE_AIRPLANE"]!!.value,
        aircraftTypeChangeBase = parameter["COST_CHANGE_ACTYPE"]!!.value,
        aircraftChangeIntraDay = parameter["DAY1_WEIGHT"]!!.value,
        aircraftChangeNextDay = parameter["DAY2_WEIGHT"]!!.value,
        aircraftChangeTertianDay = parameter["DAY3_WEIGHT"]!!.value,

        maintenanceDelay = parameter["COST_VIOLATE_MAINTAIN"]!!.value,

        transferFlight = parameter["COST_EMPTY_FLY"]!!.value,

        straightFlight = parameter["COST_FLIGHT_STRAIGHT"]!!.value,

        passengerDelay1H = parameter["COST_PASSENGER_DELAY_1_HOUR"]!!.value,
        passengerDelay4H = parameter["COST_PASSENGER_DELAY_1_TO_4_HOUR"]!!.value,
        passengerDelay8H = parameter["COST_PASSENGER_DELAY_4_TO_8_HOUR"]!!.value,
        passengerDelayOver8H = parameter["COST_FLIGHT_DELAY_8_HOUR"]!!.value,
        passengerCancel = parameter["COST_PASSENGER_CANCEL"]!!.value,
        passengerClassChange = listOf(
            Triple(PassengerClass("First"), PassengerClass("Business"), parameter["DEGRADE_FIRST_BUSINESS"]!!.value),
            Triple(PassengerClass("First"), PassengerClass("Economy"), parameter["DEGRADE_FIRST_ECONOMY"]!!.value),
            Triple(PassengerClass("Business"), PassengerClass("Economy"), parameter["DEGRADE_BUSINESS_ECONOMY"]!!.value),
        )
    )
}
