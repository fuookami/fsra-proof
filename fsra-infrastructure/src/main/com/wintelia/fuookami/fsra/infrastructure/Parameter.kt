package com.wintelia.fuookami.fsra.infrastructure

import fuookami.ospf.kotlin.utils.math.*

data class Parameter(
    val aircraftLeisure: Flt64,

    // operation
    val connectingFlightSplit: Flt64,
    val fleetBalanceSlack: Flt64,
    val flowControlSlack: Flt64,
)
