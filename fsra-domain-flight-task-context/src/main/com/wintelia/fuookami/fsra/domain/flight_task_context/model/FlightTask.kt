package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import kotlin.reflect.*
import kotlinx.datetime.*
import com.wintelia.fuookami.fsra.infrastructure.*
import fuookami.ospf.kotlin.utils.concept.AutoIndexed
import fuookami.ospf.kotlin.utils.concept.ManualIndexed
import fuookami.ospf.kotlin.utils.math.UInt64

enum class FlightTaskCategory {
    Flight {
        override val isFlightType: Boolean get() = true
    },
    VirtualFlight {
        override val isFlightType: Boolean get() = true
    },
    Maintenance,
    AOG;

    open val isFlightType: Boolean get() = false
}

abstract class FlightTaskType(
    val category: FlightTaskCategory,
    val cls: KClass<*>
) {
    abstract val type: String
    val isFlightType by category::isFlightType

    override fun hashCode(): Int {
        return cls.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FlightTaskType

        if (cls != other.cls) return false

        return true
    }

    infix fun eq(type: FlightTaskType) = this.cls == type.cls
    infix fun neq(type: FlightTaskType) = this.cls != type.cls

    infix fun eq(category: FlightTaskCategory) = this.category == category
    infix fun neq(category: FlightTaskCategory) = this.category != category

    infix fun eq(cls: KClass<*>) = this.cls == cls
    infix fun neq(cls: KClass<*>) = this.cls != cls
}

enum class FlightTaskStatus {
    NotAdvance,                    // 不可提前
    NotDelay,                       // 不可延误
    NotCancel,                      // 不可取消
    NotAircraftChange,              // 不可换飞机
    NotAircraftTypeChange,          // 不可换大机型（系列）
    NotAircraftMinorTypeChange,     // 不可换小机型（细分）
    NotTerminalChange,              // 不可换航站

    StrongLimitIgnored              // 无视强限制
};

abstract class FlightTaskPlan(
    val id: String,
    val name: String,
    val status: Set<FlightTaskStatus>
) {
    companion object {
        val NotFlightStaticConnectionTime = 5L.minutes
    }

    abstract val actualId: String
    abstract val displayName: String

    abstract val aircraft: Aircraft?
    abstract val dep: Airport
    abstract val arr: Airport
    open val depBackup: List<Airport> get() = listOf()
    open val arrBackup: List<Airport> get() = listOf()
    open fun actualArr(dep: Airport): Airport? = arr

    abstract val scheduledTime: TimeRange?
    open val time: TimeRange? get() = scheduledTime
    open val latestBeginTime: Instant?
        get() = if (status.contains(FlightTaskStatus.NotDelay)) {
            scheduledTime?.begin
        } else {
            null
        }

    open val duration: Duration? get() = time?.duration ?: scheduledTime?.duration
    open fun duration(aircraft: Aircraft): Duration {
        return aircraft.routeFlyTime[dep, arr] ?: aircraft.maxRouteFlyTime
    }

    open fun connectionTime(succTask: FlightTask?): Duration? {
        return aircraft?.let { connectionTime(it, succTask) }
    }

    open fun connectionTime(aircraft: Aircraft, succTask: FlightTask?): Duration {
        return if (succTask != null) {
            if (succTask.isFlight) {
                aircraft.connectionTime[arr] ?: aircraft.maxConnectionTime
            } else {
                NotFlightStaticConnectionTime
            }
        } else {
            0L.minutes
        }
    }

    val cancelEnabled: Boolean get() = !status.contains(FlightTaskStatus.NotCancel)
    val advanceEnabled: Boolean get() = !status.contains(FlightTaskStatus.NotAdvance)
    val delayEnabled: Boolean get() = !status.contains(FlightTaskStatus.NotDelay)
    val aircraftChangeEnabled: Boolean get() = !status.contains(FlightTaskStatus.NotAircraftChange)
    val aircraftTypeChangeEnabled: Boolean get() = !status.contains(FlightTaskStatus.NotAircraftTypeChange)
    val aircraftMinorTypeChangeEnabled: Boolean get() = !status.contains(FlightTaskStatus.NotAircraftMinorTypeChange)
    val terminalChangeEnabled: Boolean get() = !status.contains(FlightTaskStatus.NotTerminalChange)

    val strongLimitIgnored: Boolean get() = !status.contains(FlightTaskStatus.StrongLimitIgnored)
}

data class FlightTaskKey(
    val type: FlightTaskType,
    val id: String
)

abstract class FlightTask(
    val type: FlightTaskType,
    private val origin: FlightTask? = null
) : ManualIndexed() {
    val isFlight: Boolean = type.isFlightType

    abstract val plan: FlightTaskPlan

    open val id: String get() = plan.id
    open val actualId: String get() = plan.actualId
    open val name: String get() = plan.name
    open val displayName: String get() = plan.displayName
    val key: FlightTaskKey get() = FlightTaskKey(type, id)

    open val aircraft: Aircraft? get() = plan.aircraft
    open val capacity: AircraftCapacity?
        get() = if (isFlight) {
            aircraft?.capacity
        } else {
            null
        }
    open val dep: Airport get() = plan.dep
    open val arr: Airport get() = plan.arr
    open val depBackup: List<Airport> get() = plan.depBackup
    open val arrBackup: List<Airport> get() = plan.arrBackup
    open fun actualArr(dep: Airport): Airport? = plan.actualArr(dep)

    open val timeWindow: TimeRange? get() = null
    open val scheduledTime: TimeRange? get() = plan.scheduledTime
    open val time: TimeRange? get() = plan.time
    open val duration: Duration? get() = plan.duration
    open fun duration(aircraft: Aircraft): Duration {
        return plan.duration(aircraft)
    }

    open val flightHour: FlightHour?
        get() = if (isFlight) {
            duration?.let { FlightHour(it) }
        } else {
            null
        }

    open fun flightHour(aircraft: Aircraft) = FlightHour(
        if (isFlight) {
            duration(aircraft)
        } else {
            Duration.ZERO
        }
    )

    open val flightCycle
        get() = FlightCycle(
            if (isFlight) {
                UInt64.one
            } else {
                UInt64.zero
            }
        )

    open fun connectionTime(succTask: FlightTask?): Duration? = plan.connectionTime(succTask)
    open fun connectionTime(aircraft: Aircraft, succTask: FlightTask?): Duration =
        plan.connectionTime(aircraft, succTask)

    open fun latestNormalStartTime(aircraft: Aircraft): Instant {
        return if (scheduledTime != null) {
            scheduledTime!!.begin
        } else {
            timeWindow!!.end - duration(aircraft)
        }
    }

    // it is disabled to recovery if there is actual time or out time
    // it is necessary to be participated in the problem, but it is unallowed to set recovery policy
    open fun recoveryEnabled(timeWindow: TimeRange): Boolean = true
    open val maxDelay: Duration?
        get() = if (!delayEnabled) {
            0L.minutes
        } else {
            null
        }
    open val cancelEnabled get() = plan.cancelEnabled
    open val aircraftChangeEnabled get() = plan.aircraftChangeEnabled
    open val aircraftTypeChangeEnabled get() = plan.aircraftTypeChangeEnabled
    open val aircraftMinorTypeChangeEnabled get() = plan.aircraftMinorTypeChangeEnabled
    open val delayEnabled get() = plan.delayEnabled
    open val advanceEnabled get() = plan.advanceEnabled
    open val routeChangeEnabled get() = plan.terminalChangeEnabled
    open val strongLimitIgnored: Boolean get() = plan.strongLimitIgnored

    abstract val recovered: Boolean
    abstract val recoveryPolicy: RecoveryPolicy
    open fun recoveryNeeded(timeWindow: TimeRange): Boolean {
        return time == null || timeWindow.withIntersection(time!!)
    }

    open fun recoveryEnabled(policy: RecoveryPolicy): Boolean {
        return true
    }

    open val originTask: FlightTask get() = origin ?: this
    abstract fun recovery(policy: RecoveryPolicy): FlightTask

    open val advance: Duration get() = advance(plan.time)
    open val actualAdvance: Duration get() = advance(scheduledTime)
    open val delay: Duration get() = delay(plan.time)
    open val actualDelay: Duration get() = delay(scheduledTime)
    open val overMaxDelay: Duration
        get() = if (maxDelay == null || delay <= maxDelay!!) {
            0.minutes
        } else {
            delay - maxDelay!!
        }
    open val aircraftChanged: Boolean
        get() = if (!aircraftChangeEnabled) {
            false
        } else {
            recoveryPolicy.aircraft != null
        }
    open val aircraftTypeChanged: Boolean get() = aircraftTypeChange != null
    open val aircraftMinorTypeChanged: Boolean get() = aircraftMinorTypeChange != null
    open val routeChanged: Boolean
        get() = if (!routeChangeEnabled) {
            false
        } else {
            recoveryPolicy.route != null
        }
    open val aircraftChange: AircraftChange?
        get() = if (!aircraftChangeEnabled) {
            null
        } else {
            val policy = recoveryPolicy
            if (plan.aircraft != null
                && policy.aircraft != null
                && policy.aircraft != plan.aircraft
            ) {
                AircraftChange(plan.aircraft!!, policy.aircraft)
            } else {
                null
            }
        }
    open val aircraftTypeChange: AircraftTypeChange?
        get() = if (!aircraftTypeChangeEnabled) {
            null
        } else {
            val policy = recoveryPolicy
            if ((plan.aircraft != null)
                && (policy.aircraft != null)
                && (policy.aircraft.type != plan.aircraft!!.type)
            ) {
                AircraftTypeChange(plan.aircraft!!.type, policy.aircraft.type)
            } else {
                null
            }
        }
    open val aircraftMinorTypeChange: AircraftMinorTypeChange?
        get() = if (!aircraftMinorTypeChangeEnabled) {
            null
        } else {
            val policy = recoveryPolicy
            if ((plan.aircraft != null)
                && (policy.aircraft != null)
                && (policy.aircraft.minorType != plan.aircraft!!.minorType)
            ) {
                AircraftMinorTypeChange(plan.aircraft!!.minorType, policy.aircraft.minorType)
            } else {
                null
            }
        }
    open val routeChange: RouteChange?
        get() = if (!routeChangeEnabled) {
            null
        } else {
            val policy = recoveryPolicy
            if (policy.route != null
                && (policy.route.dep != dep || policy.route.arr != arr)
            ) {
                RouteChange(Route(dep, arr), policy.route)
            } else {
                null
            }
        }

    open fun arrivedWhen(airport: Airport, timeWindow: TimeRange): Boolean {
        return isFlight && time != null
                && arr == airport
                && timeWindow.contains(time!!)
    }

    open fun departedWhen(airport: Airport, timeWindow: TimeRange): Boolean {
        return isFlight && time != null
                && dep == airport
                && timeWindow.contains(time!!)
    }

    open fun locatedWhen(prevTask: FlightTask, airport: Airport, timeWindow: TimeRange): Boolean {
        val prevTime = prevTask.time
        return prevTime != null && time != null
                && prevTask.arr == airport
                && timeWindow.contains(
            TimeRange(
                begin = prevTime.end,
                end = (if (isFlight) {
                    time!!.begin
                } else {
                    time!!.end
                })
            )
        )
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FlightTask

        if (key != other.key) return false

        return true
    }

    private fun advance(targetTime: TimeRange?): Duration {
        return if (targetTime != null && time != null) {
            val advance = targetTime.begin - time!!.begin
            if (advance.isNegative()) {
                0.minutes
            } else {
                advance
            }
        } else if (timeWindow != null && time != null) {
            val advance = timeWindow!!.begin - time!!.begin
            if (advance.isNegative()) {
                0.minutes
            } else {
                advance
            }
        } else {
            0.minutes
        }
    }

    private fun delay(targetTime: TimeRange?): Duration {
        return if (targetTime != null && time != null) {
            val delay = time!!.begin - targetTime.begin
            if (delay.isNegative()) {
                0.minutes
            } else {
                delay
            }
        } else if (timeWindow != null && time != null) {
            val delay = time!!.begin - timeWindow!!.end
            if (delay.isNegative()) {
                0.minutes
            } else {
                delay
            }
        } else {
            0.minutes
        }
    }
}
