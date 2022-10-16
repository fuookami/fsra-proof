package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service

import com.wintelia.fuookami.fsra.infrastructure.dto.*

class OutputAnalyzer {
    data class Solution(
        val flights: List<RecoveryedFlightDTO>,
        val maintenance: List<RecoveryedMaintenanceDTO>
    )
}
