package com.wintelia.fuookami.fsra.io

import java.io.*
import java.time.format.*
import java.time.temporal.*
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.csv.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*

@OptIn(ExperimentalSerializationApi::class)
fun <T> readCSVFile(serializer: KSerializer<T>, path: String): List<T> {
    val csv = Csv { hasHeaderRecord = true; ignoreUnknownColumns = true; }
    return csv.decodeFromString(
        ListSerializer(serializer),
        readChineseFile(path)
    )
}

fun readRecoveryName(path: String): RecoveryPlan {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(TimeZone.currentSystemDefault().toJavaZoneId())

    val map = readCSVFile(RecoveryPlanDTO.serializer(), path).associate { Pair(it.name, it.value) }
    return RecoveryPlan(
        id = map["预案ID"]!!,
        name = map["预案名称"]!!,
        timeWindow = TimeRange(
            java.time.Instant.from(formatter.parse(map["开始时间"]!!)).truncatedTo(ChronoUnit.MINUTES).toKotlinInstant(),
            java.time.Instant.from(formatter.parse(map["结束时间"]!!)).truncatedTo(ChronoUnit.MINUTES).toKotlinInstant()
        ),
        freezingTime = map["冻结时间"]!!.toInt().minutes
    )
}

fun read(dir: String): Input {
    val path = { fileName: String -> "$dir${File.separator}$fileName" }

    return Input(
        plan = readRecoveryName(path("OperationInfo.txt")),
        parameter = readCSVFile(ParameterDTO.serializer(), path("AutoParameters.txt")).associateBy { it.name },

        airports = readCSVFile(AirportDTO.serializer(), path("airports.txt")),

        aircrafts = readCSVFile(AircraftDTO.serializer(), path("aircraft.txt")),
        aircraftTypes = readCSVFile(AircraftTypeDTO.serializer(), path("fleet.txt")),
        aircraftFee = readCSVFile(AircraftFeeDTO.serializer(), path("fee.txt")),
        aircraftMinorTypeConnectionTime = readCSVFile(
            AircraftMinorTypeConnectionTimeDTO.serializer(),
            path("fleetPassTime.txt")
        ),
        aircraftMinorTypeRouteFlyTime = readCSVFile(AircraftMinorTypeRouteFlyTimeDTO.serializer(), path("flyTime.txt")),

        flights = readCSVFile(FlightDTO.serializer(), path("flights.txt")),
        lineMaintenances = readCSVFile(LineMaintenanceDTO.serializer(), path("groundTaskA.txt")),
        scheduleMaintenances = readCSVFile(ScheduleMaintenanceDTO.serializer(), path("groundTaskB.txt")),
        aogs = readCSVFile(AOGDTO.serializer(), path("AircraftFault.txt")),
        transferFlights = readCSVFile(TransferFlightDTO.serializer(), path("allowEmptyFly.txt")),

        stopoverFlightPairs = readCSVFile(StopoverFlightPairDTO.serializer(), path("connects.txt")),
        strongRestrictions = readCSVFile(StrongRestrictionDTO.serializer(), path("StrongLimit.txt")),
        weakRestriction = readCSVFile(WeakRestrictionDTO.serializer(), path("WeakLimit.txt")),
        airportCloses = readCSVFile(AirportCloseDTO.serializer(), path("AirportClosePlan.txt")),
        airportFlowControls = readCSVFile(AirportFlowControlDTO.serializer(), path("AirportCloseTemp.txt")),

        crewAbilities = readCSVFile(CrewAbilityDTO.serializer(), path("CrewFleetAbility.txt")),
        crewSchedules = readCSVFile(CrewScheduleDTO.serializer(), path("flightRoster.txt")),
        crewConnections = readCSVFile(CrewConnectionDTO.serializer(), path("crewConnect.txt")),

        passengers = readCSVFile(PassengerDTO.serializer(), path("itineraries.txt")),
        passengerAmountLimits = readCSVFile(PassengerAmountLimitDTO.serializer(), path("limitedSeat.txt")),
        passengerTransfers = readCSVFile(PassengerTransferDTO.serializer(), path("connectTime.txt"))
    )
}
