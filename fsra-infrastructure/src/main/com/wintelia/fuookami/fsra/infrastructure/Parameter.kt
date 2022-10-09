package com.wintelia.fuookami.fsra.infrastructure

import fuookami.ospf.kotlin.utils.math.*

data class Parameter(
    val aircraftLeisure: Flt64,

    // operation
    val weakRestrictionViolation: Flt64,
    val strongRestrictionViolation: Flt64,
    val inviolableStrongRestrictionViolation: Flt64?,

    val connectingFlightSplit: Flt64,
    val stopoverFlightSplit: Flt64,
    val fleetBalanceSlack: Flt64,
    val flowControlSlack: Flt64,
)
