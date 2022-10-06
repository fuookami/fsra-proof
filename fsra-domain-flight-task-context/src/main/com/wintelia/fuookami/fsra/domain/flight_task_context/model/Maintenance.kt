package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import com.sun.tools.javac.Main
import java.util.*
import kotlin.time.*
import kotlinx.datetime.*
import com.wintelia.fuookami.fsra.infrastructure.*

enum class MaintenanceCategory {
    Line {
        override val stableStatus = setOf(
            FlightTaskStatus.NotAdvance,
            FlightTaskStatus.NotAircraftChange,
            FlightTaskStatus.NotAircraftTypeChange,
            FlightTaskStatus.NotAircraftMinorTypeChange
        )

        override fun toString() = "line"
    },
    Schedule {
        override val stableStatus = setOf(
            FlightTaskStatus.NotDelay,
            FlightTaskStatus.NotAdvance,
            FlightTaskStatus.NotAircraftChange,
            FlightTaskStatus.NotAircraftTypeChange,
            FlightTaskStatus.NotAircraftMinorTypeChange
        )

        override fun toString() = "schedule"
    };

    abstract val stableStatus: Set<FlightTaskStatus>
}

class MaintenancePlan internal constructor(
    override val aircraft: Aircraft,
    override val scheduledTime: TimeRange,
    val airport: Airport,
    val airportBackup: List<Airport>,
    val category: MaintenanceCategory,
    val expirationTime: Instant,
    status: Set<FlightTaskStatus>,
    override val actualId: String = UUID.randomUUID().toString()
) : FlightTaskPlan(
    id = "${prefix}_${actualId.replace("-", "")}",
    name = "${aircraft.regNo}_${category}_${scheduledTime.begin.toShortString()}",
    status = status
) {
    companion object {
        private const val prefix = "m"

        operator fun invoke(
            aircraft: Aircraft,
            scheduledTime: TimeRange,
            airports: List<Airport>,
            expirationTime: Instant,
            category: MaintenanceCategory,
            timeWindow: TimeRange
        ): MaintenancePlan {
            assert(airports.isNotEmpty())
            val status = category.stableStatus.toMutableSet()
            if (expirationTime <= timeWindow.end) {
                status.add(FlightTaskStatus.NotCancel)
            }

            return if (airports.size == 1) {
                status.add(FlightTaskStatus.NotTerminalChange)
                MaintenancePlan(
                    aircraft,
                    scheduledTime,
                    airports.first(),
                    emptyList(),
                    category,
                    expirationTime,
                    status
                )
            } else {
                MaintenancePlan(
                    aircraft,
                    scheduledTime,
                    airports.first(),
                    airports.subList(0, airports.size),
                    category,
                    expirationTime,
                    status
                )
            }
        }
    }

    override val displayName = "${category}-${aircraft.regNo}"
    override val dep = airport
    override val arr = airport
    override val depBackup = airportBackup
    override fun actualArr(dep: Airport): Airport? {
        return if (depBackup.contains(dep)) {
            null
        } else {
            dep
        }
    }

    override val duration get() = scheduledTime.duration
    override fun duration(aircraft: Aircraft) = scheduledTime.duration

    override fun connectionTime(nextTask: FlightTask?) = NotFlightStaticConnectionTime
    override fun connectionTime(aircraft: Aircraft, nextTask: FlightTask?) = NotFlightStaticConnectionTime
}

object MaintenanceFlightTask : FlightTaskType(FlightTaskCategory.Maintenance, MaintenanceFlightTask::class) {
    override val type get() = "Maintenance"
}

class Maintenance internal constructor(
    override val plan: MaintenancePlan,
    val recoveryTime: TimeRange? = null,
    val recoveryAirport: Airport? = null,
    origin: Maintenance? = null
) : FlightTask(MaintenanceFlightTask, origin) {
    companion object {
        operator fun invoke(plan: MaintenancePlan): Maintenance {
            return Maintenance(plan = plan)
        }

        operator fun invoke(origin: Maintenance, recoveryPolicy: RecoveryPolicy): Maintenance {
            val recoveryTime = if (recoveryPolicy.time == null || recoveryPolicy.time == origin.scheduledTime!!) {
                null
            } else {
                recoveryPolicy.time
            }
            val recoveryAirport =
                if (recoveryPolicy.route == null || (recoveryPolicy.route.first == origin.dep && recoveryPolicy.route.second == origin.arr)) {
                    null
                } else {
                    assert(recoveryPolicy.route.first == recoveryPolicy.route.second)
                    recoveryPolicy.route.first
                }

            assert(origin.recoveryEnabled(recoveryPolicy))
            return Maintenance(
                plan = origin.plan,
                recoveryTime = recoveryTime,
                recoveryAirport = recoveryAirport,
                origin = origin
            )
        }
    }

    override val dep get() = recoveryAirport ?: plan.dep
    override val arr get() = recoveryAirport ?: plan.arr

    override val time get() = recoveryTime ?: plan.time

    override fun recoveryEnabled(timeWindow: TimeRange): Boolean {
        return plan.expirationTime < timeWindow.end
                || timeWindow.contains(scheduledTime!!.begin)
    }

    override fun recoveryNeeded(timeWindow: TimeRange): Boolean {
        return plan.expirationTime < timeWindow.end
                || timeWindow.contains(scheduledTime!!.begin)
    }

    override val recovered get() = recoveryTime != null || recoveryAirport != null
    override val recoveryPolicy get() = RecoveryPolicy(null, recoveryTime, recoveryAirport?.let { Pair(it, it) })
    override fun recoveryEnabled(policy: RecoveryPolicy): Boolean {
        if (policy.aircraft != null && aircraft!! != policy.aircraft) {
            return false
        }
        if (!delayEnabled && policy.time != null && scheduledTime!!.begin < policy.time.begin) {
            return false
        }
        if (!advanceEnabled && policy.time != null && scheduledTime!!.begin > policy.time.begin) {
            return false
        }
        if (!routeChangeEnabled && policy.route != null && (policy.route.first != policy.route.second) && (dep != policy.route.first || !depBackup.contains(
                (policy.route.first)
            ))
        ) {
            return false
        }
        return true
    }

    override fun recovery(policy: RecoveryPolicy): FlightTask {
        assert(recoveryEnabled(policy))
        return Maintenance(this, recoveryPolicy)
    }

    override val routeChanged get() = recoveryAirport != null
    override val routeChange
        get() = recoveryAirport?.let {
            RouteChange(
                Pair(plan.airport, plan.airport),
                Pair(it, it)
            )
        }
}
