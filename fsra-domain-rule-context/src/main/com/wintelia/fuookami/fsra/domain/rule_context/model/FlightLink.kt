package com.wintelia.fuookami.fsra.domain.rule_context.model

import kotlin.time.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.concept.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

sealed class FlightLink(
    val type: String
) : ManualIndexed() {
    abstract val prevTask: FlightTask
    abstract val succTask: FlightTask
    abstract val splitCost: Flt64

    override fun toString() = "${type}_${prevTask.name}_${succTask.name}"
}

data class ConnectingFlightPair(
    override val prevTask: FlightTask,
    override val succTask: FlightTask,
    override val splitCost: Flt64
) : FlightLink("connecting_flight") {
    init {
        assert((prevTask is Flight) && (!prevTask.recovered))
        assert((succTask is Flight) && (!succTask.recovered))
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
) : FlightLink("stopover_flight") {
    val connectionTime = succTask.scheduledTime!!.begin - prevTask.scheduledTime!!.end

    init {
        assert((prevTask is Flight) && (!prevTask.recovered))
        assert((succTask is Flight) && (!succTask.recovered))
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

data class ConnectionTimeIgnoringFlightPair(
    override val prevTask: FlightTask,
    override val succTask: FlightTask,
    override val splitCost: Flt64
) : FlightLink("connecting_time_ignoring_flight") {
    init {
        assert((prevTask is Flight) && (!prevTask.recovered))
        assert((succTask is Flight) && (!succTask.recovered))
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

class FlightLinkMap(
    val connectingFlightPairs: List<ConnectingFlightPair>,
    val stopoverFlightPairs: List<StopoverFlightPair>,
    val connectionTimeIgnoringFlightPairs: List<ConnectionTimeIgnoringFlightPair>,
) {
    val linkPairs: List<FlightLink>
    val leftMapper: Map<FlightTaskKey, List<FlightLink>>
    val rightMapper: Map<FlightTaskKey, List<FlightLink>>

    init {
        val linkPairs = ArrayList<FlightLink>()
        linkPairs.addAll(connectingFlightPairs)
        linkPairs.addAll(stopoverFlightPairs)
        linkPairs.addAll(connectionTimeIgnoringFlightPairs)
        this.linkPairs = linkPairs

        val leftMapper = HashMap<FlightTaskKey, MutableList<FlightLink>>()
        val rightMapper = HashMap<FlightTaskKey, MutableList<FlightLink>>()
        for (pair in connectingFlightPairs) {
            if (!leftMapper.containsKey(pair.prevTask.key)) {
                leftMapper[pair.prevTask.key] = ArrayList()
            }
            leftMapper[pair.prevTask.key]!!.add(pair)
            if (!rightMapper.containsKey(pair.succTask.key)) {
                rightMapper[pair.succTask.key] = ArrayList()
            }
            rightMapper[pair.succTask.key]!!.add(pair)
        }
        this.leftMapper = leftMapper
        this.rightMapper = rightMapper
    }

    fun isStopover(flightTask: FlightTask, succFlightTask: FlightTask): Boolean {
        return leftMapper[flightTask.key]?.any { (it is StopoverFlightPair) && (it.succTask == succFlightTask.originTask) } ?: false
    }

    fun linksOf(flightTask: FlightTask): List<FlightLink> {
        return leftMapper[flightTask.key] ?: emptyList()
    }

    fun connectionTimeIfStopover(flightTask: FlightTask, succFlightTask: FlightTask): Duration? {
        val stopoverFlightPair = leftMapper[flightTask.key]?.find { (it is StopoverFlightPair) && (it.succTask == succFlightTask.originTask) }
        return (stopoverFlightPair as StopoverFlightPair?)?.connectionTime
    }

    fun isConnectionTimeIgnoring(flightTask: FlightTask, succFlightTask: FlightTask): Boolean {
        return leftMapper[flightTask.key]?.any { (it is ConnectionTimeIgnoringFlightPair) && (it.succTask == succFlightTask.originTask) } ?: false
    }
}
