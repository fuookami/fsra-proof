package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

data class Solution(
    val recoveryedFlights: List<FlightTask>,
    val canceledFlights: List<FlightTask>,
    val recoveryedMaintenances: List<FlightTask>,
    val canceledMaintenances: List<FlightTask>
)
