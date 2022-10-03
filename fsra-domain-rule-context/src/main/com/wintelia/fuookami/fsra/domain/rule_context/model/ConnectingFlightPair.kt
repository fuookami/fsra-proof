package com.wintelia.fuookami.fsra.domain.rule_context.model

import fuookami.ospf.kotlin.utils.concept.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

data class ConnectingFlightPair(
    val prevTask: FlightTask,
    val nextTask: FlightTask
): AutoIndexed(ConnectingFlightPair::class) {
    override fun toString() = "${prevTask.name}_${nextTask.name}"

    init {
        assert(prevTask is Flight)
        assert(nextTask is Flight)
    }

    override fun hashCode(): Int {
        return prevTask.hashCode() xor nextTask.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConnectingFlightPair

        if (prevTask != other.prevTask) return false
        if (nextTask != other.nextTask) return false

        return true
    }
}
