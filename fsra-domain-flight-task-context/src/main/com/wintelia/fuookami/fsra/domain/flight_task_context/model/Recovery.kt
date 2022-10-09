package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import com.wintelia.fuookami.fsra.infrastructure.*

data class RecoveryPolicy(
    val aircraft: Aircraft? = null,
    val time: TimeRange? = null,
    val route: Route? = null
) {
    val empty: Boolean = aircraft == null && time == null && route == null
}

data class AircraftChange(
    val from: Aircraft,
    val to: Aircraft
)

data class AircraftTypeChange(
    val from: AircraftType,
    val to: AircraftType
)

data class AircraftMinorTypeChange(
    val from: AircraftMinorType,
    val to: AircraftMinorType
)

data class RouteChange(
    val from: Route,
    val to: Route
)

data class RecoveryFlightTaskKey(
    val task: FlightTask,
    val policy: RecoveryPolicy
)
