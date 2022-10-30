package com.wintelia.fuookami.fsra.infrastructure.dto

import kotlinx.serialization.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import com.wintelia.fuookami.fsra.infrastructure.*

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

    val canceled: Boolean,
    val straightened: Boolean,
    val transfer: Boolean,
    val additional: Boolean,

    val delayed: Boolean,
    val advanced: Boolean,
    val aircraftChanged: Boolean,
    val aircraftMinorTypeChanged: Boolean,
)

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

    val canceled: Boolean,
    val delayed: Boolean,
    val airportChanged: Boolean,
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
