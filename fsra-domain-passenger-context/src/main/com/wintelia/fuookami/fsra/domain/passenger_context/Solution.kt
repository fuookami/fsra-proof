package com.wintelia.fuookami.fsra.domain.passenger_context

import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class Solution(
    val actualAmount: Map<FlightTask, Map<PassengerClass, UInt64>>,
    val canceledAmount: Map<FlightTask, Map<PassengerClass, UInt64>>,
    val classChangeAmount: Map<FlightTask, Map<Pair<PassengerClass, PassengerClass>, UInt64>>
)
