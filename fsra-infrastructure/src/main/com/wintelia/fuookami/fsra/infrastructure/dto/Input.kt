package com.wintelia.fuookami.fsra.infrastructure.dto

import java.time.format.*
import java.time.temporal.*
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.*
import kotlinx.serialization.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*
import java.io.Serial

private fun parseDate(str: String, formatter: DateTimeFormatter): Date {
    return Date(java.time.LocalDate.from(formatter.parse(str)).atStartOfDay().toKotlinLocalDateTime().toInstant(TimeZone.currentSystemDefault()))
}

private fun parseDateTime(str: String, formatter: DateTimeFormatter): Instant {
    return java.time.Instant.from(formatter.parse(str)).truncatedTo(ChronoUnit.MINUTES).toKotlinInstant()
}

// Airport

@Serializable
data class AirportDTO(
    @SerialName("airport")
    val icao: ICAO,
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
    val endTimeStr: String,             // DateTime
    @SerialName("is_abnormal")
    val disabledBin: UInt64,
    @SerialName("aircraft_avai_time")
    val enabledTimeStr: String          // DateTime
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy/M/d H:m").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val enabled: Boolean get() = disabledBin != UInt64.one
    val endTime get() = parseDateTime(endTimeStr, formatter)
    val enabledTime get() = parseDateTime(enabledTimeStr, formatter)
}

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

@Serializable
data class AircraftMinorTypeConnectionTimeDTO(
    val airport: ICAO,
    @SerialName("ac_type")
    val minorType: AircraftMinorTypeCode,
    @SerialName("pass_time")
    val connectionTime: UInt64
)

// Flight

@Serializable
data class FlightDTO(
    @SerialName("flight_id")
    val id: String,
    @SerialName("flight_date")
    val dateStr: String,                // Date
    @SerialName("d_or_i")
    val region: CString,                // "国内" 或 "国外"
    @SerialName("flight_no")
    val no: String,
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("std")
    val stdStr: String,                 // DateTime
    @SerialName("sta")
    val staStr: String,                 // DateTime
    @SerialName("etd")
    val etdStr: String?,                // DateTime
    @SerialName("eta")
    val etaStr: String?,                // DateTime
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("ac_type")
    val acType: AircraftMinorTypeCode,
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
    @SerialName("weight")
    val weight: Flt64,
    @SerialName("conn_order_num")
    val connectedPassengerAmount: UInt64,
    @SerialName("is_conn_flight")
    val stopoverFlightBin: UInt64,
    @SerialName("allow_ahead")
    val advanceEnabledBin: UInt64,
    @SerialName("allow_delay")
    val delayEnabledBin: UInt64,
    @SerialName("allow_change_actype_group")
    val aircraftTypeChangeEnabledBin: UInt64,
    @SerialName("allow_change_actype")
    val aircraftMinorTypeChangeEnabledBin: UInt64,
    @SerialName("allow_change_aircraft")
    val aircraftChangeEnabledBin: UInt64,
    @SerialName("allow_cancel")
    val cancelEnabledBin: UInt64,
    @SerialName("p_or_c")
    val passengerCargoType: String,     // "passenger" or "cargo"
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/M/d").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/M/d H:m").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val date get() = parseDate(dateStr, dateFormatter)
    val std get() = parseDateTime(stdStr, dateTimeFormatter)
    val sta get() = parseDateTime(staStr, dateTimeFormatter)
    val etd get() = if (!etdStr.isNullOrBlank()) { parseDateTime(etdStr, dateTimeFormatter) } else { null }
    val eta get() = if (!etaStr.isNullOrBlank()) { parseDateTime(etaStr, dateTimeFormatter) } else { null }

    val stopoverFlight: Boolean get() = stopoverFlightBin == UInt64.one
    val advanceEnabled: Boolean get() = advanceEnabledBin == UInt64.one
    val delayEnabled: Boolean get() = delayEnabledBin == UInt64.one
    val aircraftTypeChangeEnabled: Boolean get() = aircraftTypeChangeEnabledBin == UInt64.one
    val aircraftMinorTypeChangeEnabled: Boolean get() = aircraftMinorTypeChangeEnabledBin == UInt64.one
    val aircraftChangeEnabled: Boolean get() = aircraftChangeEnabledBin == UInt64.one
    val cancelEnabled: Boolean get() = cancelEnabledBin == UInt64.one
}

// Maintenance

@Serializable
data class LineMaintenanceDTO(
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("start_time")
    val beginTimeStr: String,       // DateTime
    @SerialName("end_time")
    val endTimeStr: String,         // DateTime
    @SerialName("airports")
    val airportList: String,        // [ICAO]
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val airports get() = airportList.split("|").mapNotNull { if (it.isNotEmpty()) { ICAO(it) } else { null } }
    val beginTime get() = parseDateTime(beginTimeStr, formatter)
    val endTime get() = parseDateTime(endTimeStr, formatter)
}

@Serializable
data class ScheduleMaintenanceDTO(
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("start_time")
    val beginTimeStr: String,       // DateTime
    @SerialName("end_time")
    val endTimeStr: String,         // DateTime
    @SerialName("airports")
    val airportList: String,        // [ICAO]
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val airports get() = airportList.split("|").mapNotNull { if (it.isNotEmpty()) { ICAO(it) } else { null } }

    val beginTime get() = parseDateTime(beginTimeStr, formatter)
    val endTime get() = parseDateTime(endTimeStr, formatter)
}

// AOG

@Serializable
data class AOGDTO(
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("start_time")
    val beginTimeStr: String,       // DateTime
    @SerialName("end_time")
    val endTimeStr: String,         // DateTime
    @SerialName("airport")
    val airportList: String,        // [ICAO]
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val airports get() = airportList.split("|").mapNotNull { if (it.isNotEmpty()) { ICAO(it) } else { null } }

    val beginTime get() = parseDateTime(beginTimeStr, formatter)
    val endTime get() = parseDateTime(endTimeStr, formatter)
}

// Transfer Flight

@Serializable
data class TransferFlightDTO(
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("start_time")
    val beginTimeStr: String,       // DateTime
    @SerialName("end_time")
    val endTimeStr: String,         // DateTime
    @SerialName("fly_time")
    val flyTime: UInt64
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val beginTime get() = parseDateTime(beginTimeStr, formatter)
    val endTime get() = parseDateTime(endTimeStr, formatter)
}

// Stopover Flight Pair

@Serializable
data class StopoverFlightPairDTO(
    @SerialName("pre_flight_id")
    val prevFlightId: String,
    @SerialName("connect_flight_id")
    val nextFlightId: String,
    @SerialName("is_same_aircraft")
    val sameAircraftBin: UInt64
) {
    val sameAircraft: Boolean get() = sameAircraftBin == UInt64.one
}

// Restriction

@Serializable
data class StrongRestrictionDTO(
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("ac_reg")
    val acReg: AircraftRegisterNumber,
    @SerialName("flight_id")
    val flightId: String,
    @SerialName("limit_type")
    val limitType: String
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
    val beginDateStr: String,       // Date
    @SerialName("begin_close_time")
    val beginCloseTime: String,     // DayTime
    @SerialName("end_date")
    val endDateStr: String,         // Date
    @SerialName("end_close_time")
    val endCloseTime: String,       // DayTime
    @SerialName("ac_types")
    val acTypeList: String,
) {
    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
        private val timeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val times: List<TimeRange>
        get() {
            val times = ArrayList<TimeRange>()

            var date = parseDate(beginDateStr, dateFormatter)
            val endDate = parseDate(endDateStr, dateFormatter)
            var beginTime = parseDateTime("$beginDateStr $beginCloseTime", timeFormatter)
            var endTime = parseDateTime("$beginDateStr $endCloseTime", timeFormatter)
            if (date == endDate) {
                times.add(TimeRange(beginTime, endTime))
            } else {
                while (date leq endDate) {
                    times.add(TimeRange(beginTime, endTime))
                    date += 1.days
                    beginTime += 1.days
                    endTime += 1.days
                }
            }
            return times
        }

    val minorTypes = acTypeList.split(",").mapNotNull { if (it.isNotEmpty()) { AircraftMinorTypeCode(it) } else { null } }
}

@Serializable
data class AirportFlowControlDTO(
    val airport: ICAO,
    @SerialName("begin_close_time")
    val beginTimeStr: String,       // DateTime
    @SerialName("end_close_time")
    val endTimeStr: String,         // DateTime
    @SerialName("affect_time")
    val type: CString,              // ”起飞“、”降落“、”停机“ 或 "起降"
    val capacity: UInt64
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val beginTime get() = parseDateTime(beginTimeStr, formatter)
    val endTime get() = parseDateTime(endTimeStr, formatter)
}

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
    val sameAircraftBin: UInt64
) {
    val sameAircraft: Boolean get() = sameAircraftBin == UInt64.one
}

// Passenger

@Serializable
data class PassengerDTO(
    @SerialName("passenger_id")
    val id: String,
    @SerialName("flight_date")
    val flightDateStr: String,      // DateTime
    @SerialName("dep_airport")
    val dep: ICAO,
    @SerialName("arr_airport")
    val arr: ICAO,
    @SerialName("passenger_num")
    val num: UInt64
) {
    companion object {
        private val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
    }

    val flightDate get() = parseDate(flightDateStr, formatter)
}

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
    val transferFlights: List<TransferFlightDTO>,

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
