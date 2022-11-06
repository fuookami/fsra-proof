package com.wintelia.fuookami.fsra.infrastructure

import kotlin.time.*
import kotlin.time.Duration.Companion.seconds
import fuookami.ospf.kotlin.utils.math.*

data class FreeAircraftSelectorConfiguration(
    val fixBar: Flt64 = Flt64(-1e-4),
    val badReducedAmount: UInt64 = UInt64(20UL),
    val noBusyAmount: UInt64 = UInt64(5UL),
    val highCostAmount: UInt64 = UInt64(4UL),
    val highCostDensityAmount: UInt64 = UInt64(3UL),
    val highFlowControlCostAmount: UInt64 = UInt64(4UL),
    val highDelayAmount: UInt64 = UInt64(2UL),
    val highAircraftChangeAmount: UInt64 = UInt64(2UL),
    val randAmount: UInt64 = UInt64(2UL),
    val tabuAmount: UInt64 = UInt64(53UL)
)

data class Configuration(
    val withRedundancy: Boolean = false,
    val withPassenger: Boolean = true,
    val withCargo: Boolean = false,
    val withCrew: Boolean = false,
    val withOrderChange: Boolean = false,

    val solver: String = "cplex",
    val multiThread: Boolean = false,
    val badReducedAmount: UInt64 = UInt64(20UL),
    val maximumLabelPerNode: UInt64 = UInt64(10000UL),
    val maximumColumnGeneratedPerAircraft: UInt64 = UInt64(60UL),
    val maximumColumnAmount: UInt64 = UInt64(50000UL),
    val minimumColumnAmountPerAircraft: UInt64 = UInt64.zero,
    val timeLimit: Duration = 30000.seconds,

    val freeAircraftSelectorConfiguration: FreeAircraftSelectorConfiguration = FreeAircraftSelectorConfiguration()
) {
    val onlyWithCargo: Boolean get() = withCargo && !withPassenger
    val onlyWithPassenger: Boolean get() = withPassenger && !withCargo
    val flightTaskTimeNeeded = withPassenger || withCargo || withCrew
}
