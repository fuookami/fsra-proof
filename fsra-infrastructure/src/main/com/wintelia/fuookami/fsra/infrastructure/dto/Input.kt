package com.wintelia.fuookami.fsra.infrastructure.dto

import kotlinx.serialization.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*

// Airport

@Serializable
data class AirportDTO(
    val airport: ICAO,
    @SerialName("airport_type")
    val type: CString                   // "国内" 或 "国外"
)

// Aircraft

@Serializable
data class AircraftDTO(
    @SerialName("ac_reg")
    val regNo: AircraftRegisterNumber,
    @SerialName("ac_type")
    val minorType: AircraftMinorTypeCode,
    @SerialName("passenger_cargo_type")
    val passengerCargoType: String,     // "passenger" or "cargo"

    @SerialName("seat_num")
    val seatNum: UInt64,
    @SerialName("F_class_num")
    val firstClassNum: UInt64,
    @SerialName("B_class_num")
    val businessClassNum: UInt64,
    @SerialName("E_class_num")
    val economyClassNum: UInt64,

    @SerialName("end_airport")
    val endAirport: ICAO,
    @SerialName("end_time")
    val endTime: String,                // DateTime
    @SerialName("is_abnormal")
    val enabled: Boolean,
    @SerialName("aircraft_avai_time")
    val enabledTime: String             // DateTime
)

@Serializable
data class AircraftTypeDTO(
    @SerialName("fleet_id")
    val type: AircraftTypeCode,
    @SerialName("ac_type")
    val minorType: AircraftMinorTypeCode
)

@Serializable
data class AircraftFeeDTO(
    @SerialName("ac_type")
    val minorType: AircraftMinorTypeCode,
    @SerialName("cost_per_hour")
    val costPerHour: Flt64
)

@Serializable
data class AircraftMinorTypeConnectionTimeDTO(
    val airport: ICAO,
    @SerialName("ac_type")
    val minorType: AircraftMinorTypeCode,
    @SerialName("pass_time")
    val connectionTime: UInt64
)

@Serializable
data class AircraftMinorTypeRouteFlyTimeDTO(
    @SerialName("ac_type")
    val minorType: AircraftMinorTypeCode,
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("fly_time")
    val routeFlyTime: UInt64
)

// Flight

@Serializable
data class FlightDTO(
    @SerialName("flight_id")
    val id: String,
    @SerialName("flight_date")
    val date: String,                   // Date
    @SerialName("region")
    val region: String,                 // "国内" 或 "国外"
    @SerialName("flight_code")
    val no: String,
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    val std: String,                    // DateTime
    val sta: String,                    // DateTime
    @SerialName("order_num")
    val orderNum: UInt64,
    @SerialName("seat_num")
    val seatNum: UInt64,
    @SerialName("F_class_order_num")
    val firstClassNum: UInt64,
    @SerialName("B_class_order_num")
    val businessClassNum: UInt64,
    @SerialName("E_class_order_num")
    val economyClassNum: UInt64,
    @SerialName("importance_num")
    val weight: Flt64,
    @SerialName("conn_order_num")
    val connectedPassengerAmount: UInt64,
    @SerialName("is_conn_flight")
    val stopoverFlight: Boolean,
    @SerialName("allow_ahead")
    val advanceEnabled: Boolean,
    @SerialName("allow_delay")
    val delayEnabled: Boolean,
    @SerialName("allow_change_actype_group")
    val aircraftTypeChangeEnabled: Boolean,
    @SerialName("allow_change_actype")
    val aircraftMinorTypeChangeEnabled: Boolean,
    @SerialName("allow_change_aircraft")
    val aircraftChangeEnabled: Boolean,
    @SerialName("allow_cancel")
    val cancelEnabled: Boolean,
    @SerialName("passenger_cargo_type")
    val passengerCargoType: String,     // "passenger" or "cargo"
)

// Maintenance

@Serializable
data class LineMaintenanceDTO(
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("start_time")
    val beginTime: String,          // DateTime
    @SerialName("end_time")
    val endTime: String,            // DateTime
    @SerialName("airports")
    val airportList: String,        // [ICAO]
) {
    val airports get() = airportList.split(",").map { ICAO(it) }
}

@Serializable
data class ScheduleMaintenanceDTO(
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("start_time")
    val beginTime: String,          // DateTime
    @SerialName("end_time")
    val endTime: String,            // DateTime
    @SerialName("airports")
    val airportList: String,        // [ICAO]
) {
    val airports get() = airportList.split(",").map { ICAO(it) }
}

// AOG

@Serializable
data class AOGDTO(
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("start_time")
    val beginTime: String,          // DateTime
    @SerialName("end_time")
    val endTime: String,            // DateTime
    @SerialName("airport")
    val airport: ICAO
)

// Transfer Flight

@Serializable
data class TransferFlightDTO(
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("start_time")
    val beginTime: String,          // DateTime
    @SerialName("end_time")
    val endTime: String,            // DateTime
    @SerialName("fly_time")
    val flyTime: UInt64
)

// Stopover Flight Pair

@Serializable
data class StopoverFlightPairDTO(
    @SerialName("prev_flight_id")
    val prevFlightId: String,
    @SerialName("connect_flight_id")
    val nextFlightId: String,
    @SerialName("is_same_aircraft")
    val sameAircraft: Boolean
)

// Restriction

@Serializable
data class StrongRestrictionDTO(
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("penalty_value")
    val weight: Flt64
)

@Serializable
data class WeakRestrictionDTO(
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("penalty_value")
    val weight: Flt64
)

// Flow Control

@Serializable
data class AirportCloseDTO(
    val airport: ICAO,
    @SerialName("begin_date")
    val beginDate: String,          // Date
    @SerialName("begin_close_time")
    val beginCloseTime: String,     // DayTime
    @SerialName("end_date")
    val endDate: String,            // Date
    @SerialName("end_close_time")
    val endCloseTime: String,       // DayTime
)

@Serializable
data class AirportFlowControlDTO(
    val airport: ICAO,
    @SerialName("begin_close_time")
    val beginTime: String,          // DateTime
    @SerialName("end_close_time")
    val endTime: String,            // DateTime
    @SerialName("affect_time")
    val type: String,               // ”起飞“、”降落“、”停机“ 或 "起降"
    val capacity: UInt64
)

// Crew

@Serializable
data class CrewAbilityDTO(
    @SerialName("crew_id")
    val crewId: String,
    @SerialName("ac_type")
    val minorType: AircraftMinorTypeCode
)

@Serializable
data class CrewScheduleDTO(
    @SerialName("flight_id")
    val flightId: String,
    @SerialName("crew_id")
    val crewId: String,
    @SerialName("position")
    val crewClass: String
)

@Serializable
data class CrewConnectionDTO(
    @SerialName("pre_flight_id")
    val prefFlightId: String,
    @SerialName("connect_flight_id")
    val nextFlightId: String,
    @SerialName("is_same_aircraft")
    val sameAircraft: Boolean
)

// Passenger

@Serializable
data class PassengerDTO(
    @SerialName("passenger_id")
    val id: String,
    @SerialName("flight_date")
    val flightDate: String,         // DateTime
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("passenger_num")
    val num: UInt64
)

@Serializable
data class PassengerAmountLimitDTO(
    @SerialName("flight_id")
    val flightId: String,
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("limited_seat")
    val num: UInt64
)

@Serializable
data class PassengerTransferDTO(
    @SerialName("trans_pref_flight_id")
    val prefFlightId: String,
    @SerialName("trans_conn_flight_id")
    val nextFlightId: String,
    @SerialName("min_trans_time")
    val transferTime: UInt64,
    @SerialName("trans_num")
    val transferNum: UInt64
)

// other

@Serializable
data class RecoveryPlanDTO(
    @SerialName("参数名")
    val name: String,
    @SerialName("参数值")
    val value: String
)

@Serializable
data class ParameterDTO(
    @SerialName("param_name")
    val name: String,
    @SerialName("param_value")
    val value: Flt64,
    @SerialName("param_description")
    val description: String,
)

data class Input(
    val plan: RecoveryPlan,
    val parameter: Map<String, ParameterDTO>,

    val airports: List<AirportDTO>,

    val aircrafts: List<AircraftDTO>,
    val aircraftTypes: List<AircraftTypeDTO>,
    val aircraftFee: List<AircraftFeeDTO>,
    val aircraftMinorTypeConnectionTime: List<AircraftMinorTypeConnectionTimeDTO>,
    val aircraftMinorTypeRouteFlyTime: List<AircraftMinorTypeRouteFlyTimeDTO>,

    val flights: List<FlightDTO>,
    val lineMaintenances: List<LineMaintenanceDTO>,
    val scheduleMaintenances: List<ScheduleMaintenanceDTO>,
    val aogs: List<AOGDTO>,
    val transferFlight: List<TransferFlightDTO>,

    val stopoverFlightPairs: List<StopoverFlightPairDTO>,
    val strongRestrictions: List<StrongRestrictionDTO>,
    val weakRestriction: List<WeakRestrictionDTO>,
    val airportCloses: List<AirportCloseDTO>,
    val airportFlowControls: List<AirportFlowControlDTO>,

    val crewAbilities: List<CrewAbilityDTO>,
    val crewSchedules: List<CrewScheduleDTO>,
    val crewConnections: List<CrewConnectionDTO>,

    val passengers: List<PassengerDTO>,
    val passengerAmountLimits: List<PassengerAmountLimitDTO>,
    val passengerTransfers: List<PassengerTransferDTO>
)
