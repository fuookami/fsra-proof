package com.wintelia.fuookami.fsra.domain.rule_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.concept.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

sealed class FlightLink : ManualIndexed() {
    abstract val prevTask: FlightTask
    abstract val succTask: FlightTask
    abstract val splitCost: Flt64
}

data class ConnectingFlightPair(
    override val prevTask: FlightTask,
    override val succTask: FlightTask,
    override val splitCost: Flt64
): FlightLink() {
    override fun toString() = "${prevTask.name}_${succTask.name}"

    init {
        assert(prevTask is Flight)
        assert(succTask is Flight)
    }

    override fun hashCode(): Int {
        return prevTask.hashCode() xor succTask.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConnectingFlightPair

        if (prevTask != other.prevTask) return false
        if (succTask != other.succTask) return false

        return true
    }
}

data class StopoverFlightPair(
    override val prevTask: FlightTask,
    override val succTask: FlightTask,
    override val splitCost: Flt64
): FlightLink() {
    val connectionTime = succTask.scheduledTime!!.begin - prevTask.scheduledTime!!.end

    init {
        assert(prevTask is Flight)
        assert(succTask is Flight)
    }
}

class FlightLinkMap(
    val connectingFlightPairs: List<ConnectingFlightPair>,
    val stopoverFlightPairs: List<StopoverFlightPair>,
) {
    val linkPairs: List<FlightLink>
    val leftMapper: Map<FlightTask, List<FlightLink>>
    val rightMapper: Map<FlightTask, List<FlightLink>>

    init {
        val linkPairs = ArrayList<FlightLink>()
        linkPairs.addAll(connectingFlightPairs)
        linkPairs.addAll(stopoverFlightPairs)
        this.linkPairs = linkPairs

        val leftMapper = HashMap<FlightTask, MutableList<FlightLink>>()
        val rightMapper = HashMap<FlightTask, MutableList<FlightLink>>()
        for (pair in connectingFlightPairs) {
            if (leftMapper.containsKey(pair.prevTask)) {
                leftMapper[pair.prevTask] = ArrayList()
            }
            leftMapper[pair.prevTask]!!.add(pair)
            if (rightMapper.containsKey(pair.succTask)) {
                rightMapper[pair.succTask] = ArrayList()
            }
            rightMapper[pair.succTask]!!.add(pair)
        }
        this.leftMapper = leftMapper
        this.rightMapper = rightMapper
    }
}
