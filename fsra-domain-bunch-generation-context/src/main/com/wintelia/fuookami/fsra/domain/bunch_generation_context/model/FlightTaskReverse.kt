package com.wintelia.fuookami.fsra.domain.bunch_generation_context.model

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class FlightTaskReverse(
    private val symmetricalPairs: MutableList<Pair> = ArrayList(),
    private val leftMapper: MutableMap<FlightTaskKey, MutableList<Pair>>,
    private val rightMapper: MutableMap<FlightTaskKey, MutableList<Pair>>
) {
    data class Pair(
        val prevTask: FlightTask,
        val nextTask: FlightTask,
        val symmetrical: Boolean
    )


}
