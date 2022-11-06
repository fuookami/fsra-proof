package com.wintelia.fuookami.fsra.infrastructure.dto

import kotlinx.serialization.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import com.wintelia.fuookami.fsra.infrastructure.*

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RecoveryedFlightDTO(
    val recoveryPlanId: String,
    val id: String,
    val no: String,
    val dep: ICAO,
    val arr: ICAO,
    val oldAcType: AircraftMinorTypeCode?,
    val newAcType: AircraftMinorTypeCode?,
    val oldAcReg: AircraftRegisterNumber?,
    val newAcReg: AircraftRegisterNumber?,
    val date: String?,                          // Date
    val std: String?,                           // DateTime
    val sta: String?,                           // DateTime
    val etd: String?,                           // DateTime
    val eta: String?,                           // DateTime

    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val delayTime: String = "0 m",

    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var oldFirstClassCapacity: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var firstClassCapacity: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var oldFirstClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var firstClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var firstClassCanceledAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var firstClassFromBusinessClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var firstClassFromEconomyClassAmount: UInt64 = UInt64.zero,

    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var oldBusinessClassCapacity: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var businessClassCapacity: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var oldBusinessClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var businessClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var businessClassCanceledAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var businessClassFromFirstClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var businessClassFromEconomyClassAmount: UInt64 = UInt64.zero,

    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var oldEconomyClassCapacity: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var economyClassCapacity: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var oldEconomyClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var economyClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var economyClassCanceledAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var economyClassFromFirstClassAmount: UInt64 = UInt64.zero,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    var economyClassFromBusinessClassAmount: UInt64 = UInt64.zero,

    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val canceled: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val straightened: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val transfer: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val additional: Boolean = false,

    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val delayed: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val advanced: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val aircraftChanged: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val aircraftMinorTypeChanged: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class RecoveryedMaintenanceDTO(
    val recoveryPlanId: String,
    val id: String,
    val acReg: AircraftRegisterNumber,
    val scheduledBeginTime: String?,            // DateTime
    val scheduledEndTime: String?,              // DateTime
    val scheduledAirport: ICAO?,
    val estimatedBeginTime: String?,            // DateTime
    val estimatedEndTime: String?,              // DateTime
    val estimatedAirport: ICAO?,

    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val canceled: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val delayed: Boolean = false,
    @EncodeDefault(mode = EncodeDefault.Mode.ALWAYS)
    val airportChanged: Boolean = false,
)

@Serializable
data class Performance(
    val mainProblemSolvingTimes: UInt64,
    val mainProblemSolvingTime: UInt64,         // ms
    val mainProblemModelingTime: UInt64,        // ms
    val subProblemSolvingTimes: UInt64,
    val subProblemSolvingTime: UInt64,          // ms
    var useTime: UInt64                         // ms
)

@Serializable
data class Output(
    val recoveryPlanId: String,
    val success: Boolean,
    val errorCode: UInt64?,
    val errorMessage: String?,
    val obj: Flt64?,
    val flights: List<RecoveryedFlightDTO>?,
    val maintenances: List<RecoveryedMaintenanceDTO>?,
    var performance: Performance? = null
) {
    constructor(recoveryPlanId: String, error: Error) : this(
        recoveryPlanId = recoveryPlanId,
        success = false,
        errorCode = error.code().toUInt64(),
        errorMessage = error.message(),
        obj = null,
        flights = null,
        maintenances = null
    )
}
