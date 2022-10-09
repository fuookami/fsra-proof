package com.wintelia.fuookami.fsra.domain.rule_context.model

import java.util.*
import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

private fun toAmount(hit: Boolean): UInt64 {
    return if (hit) UInt64.one else UInt64.zero
}

enum class FlowControlScene {
    Departure {
        override operator fun invoke(
            prevTask: FlightTask?,
            task: FlightTask?,
            airport: Airport,
            time: TimeRange,
            condition: FlowControlCondition
        ): Boolean {
            return task != null && condition(task) && task.departedWhen(airport, time)
        }

        override fun toString() = "DEP"
        override fun toChineseString() = CString("起飞")
    },
    Arrival {
        override fun invoke(
            prevTask: FlightTask?,
            task: FlightTask?,
            airport: Airport,
            time: TimeRange,
            condition: FlowControlCondition
        ): Boolean {
            return task != null && task.arrivedWhen(airport, time)
        }

        override fun toString() = "ARR"
        override fun toChineseString() = CString("降落")
    },
    DepartureArrival {
        override fun invoke(
            prevTask: FlightTask?,
            task: FlightTask?,
            airport: Airport,
            time: TimeRange,
            condition: FlowControlCondition
        ): Boolean {
            return Departure(prevTask, task, airport, time, condition)
                    || Arrival(prevTask, task, airport, time, condition)
        }

        override fun invoke(
            bunch: FlightTaskBunch,
            airport: Airport,
            time: TimeRange,
            condition: FlowControlCondition
        ): UInt64 {
            return Departure(bunch, airport, time, condition) + Arrival(bunch, airport, time, condition)
        }

        override fun toString() = "DA"
        override fun toChineseString() = CString("起降")
    },
    Stay {
        override fun invoke(
            prevTask: FlightTask?,
            task: FlightTask?,
            airport: Airport,
            time: TimeRange,
            condition: FlowControlCondition
        ): Boolean {
            return if (prevTask != null && task != null && condition(task)) {
                task.locatedWhen(prevTask, airport, time)
            } else if (prevTask != null && condition(prevTask)) {
                prevTask.arrivedWhen(airport, time)
            } else if (task != null && condition(task)) {
                task.departedWhen(airport, time)
            } else {
                false
            }
        }

        override fun toString() = "STAY"
        override fun toChineseString() = CString("停机")
    };

    companion object {
        operator fun invoke(str: CString): FlowControlScene? {
            return FlowControlScene.values().find { it.toChineseString() == str }
        }
    }

    abstract operator fun invoke(
        prevTask: FlightTask?,
        task: FlightTask?,
        airport: Airport,
        time: TimeRange,
        condition: FlowControlCondition
    ): Boolean

    open operator fun invoke(
        bunch: FlightTaskBunch,
        airport: Airport,
        time: TimeRange,
        condition: FlowControlCondition
    ): UInt64 {
        var ret = UInt64.zero
        for (i in bunch.flightTasks.indices) {
            ret += toAmount(
                if (i == 0) {
                    this(null, bunch.flightTasks[i], airport, time, condition)
                } else {
                    this(bunch.flightTasks[i - 1], bunch.flightTasks[i], airport, time, condition)
                }
            )
        }
        return ret
    }

    abstract fun toChineseString(): CString
}

data class FlowControlCondition(
    val flightTypes: Set<FlightType> = emptySet(),
    val aircraftMinorTypes: Set<AircraftMinorType> = emptySet()
) {
    operator fun invoke(flightTask: FlightTask): Boolean {
        if (flightTask.isFlight) {
            return false
        }

        if (flightTypes.isNotEmpty()) {
            val type = when (flightTask) {
                is Flight -> {
                    flightTask.plan.type
                }

                else -> {
                    FlightType(flightTask.dep, flightTask.arr)
                }
            }
            if (!flightTypes.contains(type)) {
                return false
            }
        }

        if (flightTask.aircraft != null && aircraftMinorTypes.isNotEmpty()) {
            if (!aircraftMinorTypes.contains(flightTask.aircraft!!.minorType)) {
                return false
            }
        }

        return true
    }
}

data class FlowControlCapacity(
    val amount: UInt64,
    val interval: Duration,
) {
    companion object {
        val close = FlowControlCapacity(UInt64.zero, 30.minutes)
    }

    val closed = amount == UInt64.zero

    override fun toString() = if (closed) "closed" else "${amount}_${interval.toInt(DurationUnit.MINUTES)}m"
}

class FlowControl(
    val id: String = UUID.randomUUID().toString(),
    val airport: Airport,
    val time: TimeRange,
    val condition: FlowControlCondition = FlowControlCondition(),
    val scene: FlowControlScene,
    val capacity: FlowControlCapacity,
    val name: String = "${airport.icao}_${scene}_${capacity}_${time.begin.toShortString()}_${time.end.toShortString()}"
) {
    val closed by capacity::closed
}
