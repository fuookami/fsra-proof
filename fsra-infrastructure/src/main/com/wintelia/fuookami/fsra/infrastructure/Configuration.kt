package com.wintelia.fuookami.fsra.infrastructure

import kotlin.time.*
import kotlin.time.Duration.Companion.seconds
import fuookami.ospf.kotlin.utils.math.*

data class Configuration(
    val withRedundancy: Boolean,
    val withPassenger: Boolean,
    val withCargo: Boolean,
    val withCrew: Boolean,

    val solver: String,
    val badReducedAmount: UInt64 = UInt64(20UL),
    val maximumColumnAmount: UInt64 = UInt64(20000UL),
    val minimumColumnAmountPerAircraft: UInt64 = UInt64.zero,
    val timeLimit: Duration = 30000.seconds
) {
    val flightTaskTimeNeeded = withPassenger || withCargo
}
