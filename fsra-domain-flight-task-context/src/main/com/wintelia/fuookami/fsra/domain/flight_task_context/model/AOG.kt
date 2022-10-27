package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import java.util.*
import com.wintelia.fuookami.fsra.infrastructure.*

class AOGPlan(
    override val aircraft: Aircraft,
    override val scheduledTime: TimeRange,
    val airport: Airport,
    val airportBackup: List<Airport>,
    status: Set<FlightTaskStatus>,
    override val actualId: String = UUID.randomUUID().toString()
) : FlightTaskPlan(
    id = "${prefix}_${actualId.replace("-", "")}",
    name = "${aircraft.regNo}_AOG_${scheduledTime.begin.toShortString()}",
    status = status
) {
    companion object {
        val stableStatus = setOf(
            FlightTaskStatus.NotCancel,
            FlightTaskStatus.NotDelay,
            FlightTaskStatus.NotAdvance,
            FlightTaskStatus.NotAircraftChange,
            FlightTaskStatus.NotAircraftTypeChange,
            FlightTaskStatus.NotAircraftMinorTypeChange
        )

        private const val prefix = "a"

        operator fun invoke(aircraft: Aircraft, scheduledTime: TimeRange, airports: List<Airport>): AOGPlan {
            assert(airports.isNotEmpty())
            val status = stableStatus.toMutableSet()
            return if (airports.size == 1) {
                AOGPlan(
                    aircraft = aircraft,
                    scheduledTime = scheduledTime,
                    airport = airports.first(),
                    airportBackup = emptyList(),
                    status = status
                )
            } else {
                AOGPlan(
                    aircraft = aircraft,
                    scheduledTime = scheduledTime,
                    airport = airports.first(),
                    airportBackup = airports.subList(1, airports.size),
                    status = status
                )
            }
        }
    }

    override val displayName = "AOG"
    override val dep = airport
    override val arr = airport
    override val depBackup = airportBackup
    override fun actualArr(dep: Airport): Airport? {
        return if (depBackup.contains(dep)) {
            dep
        } else {
            null
        }
    }

    override val duration get() = scheduledTime.duration
    override fun duration(aircraft: Aircraft) = scheduledTime.duration

    override fun connectionTime(succTask: FlightTask?) = NotFlightStaticConnectionTime
    override fun connectionTime(aircraft: Aircraft, succTask: FlightTask?) = NotFlightStaticConnectionTime
}

object AOGFlightTask : FlightTaskType(FlightTaskCategory.Maintenance, AOGFlightTask::class) {
    override val type get() = "AOG"
}

class AOG internal constructor(
    override val plan: AOGPlan,
    val recoveryAirport: Airport? = null,
    origin: AOG? = null
) : FlightTask(AOGFlightTask, origin) {
    companion object {
        operator fun invoke(plan: AOGPlan) = AOG(plan)

        operator fun invoke(origin: AOG, recoveryPolicy: RecoveryPolicy): AOG {
            val recoveryAirport =
                if (recoveryPolicy.route == null || (recoveryPolicy.route.dep == origin.dep && recoveryPolicy.route.arr == origin.arr)) {
                    null
                } else {
                    assert(recoveryPolicy.route.dep == recoveryPolicy.route.arr)
                    recoveryPolicy.route.dep
                }

            assert(origin.recoveryEnabled(recoveryPolicy))
            return AOG(
                plan = origin.plan,
                recoveryAirport = recoveryAirport,
                origin = origin
            )
        }
    }

    override val dep get() = recoveryAirport ?: plan.dep
    override val arr get() = recoveryAirport ?: plan.arr

    override val recovered get() = recoveryAirport != null
    override val recoveryPolicy get() = RecoveryPolicy()
    override fun recoveryEnabled(policy: RecoveryPolicy): Boolean {
        if (policy.aircraft != null && aircraft!! != policy.aircraft) {
            return false
        }
        if (policy.time != null && time != policy.time) {
            return false
        }
        if (!routeChangeEnabled && policy.route != null
            && (policy.route.dep != policy.route.arr)
            && (dep != policy.route.dep || !depBackup.contains(policy.route.dep))
        ) {
            return false
        }
        return super.recoveryEnabled(policy)
    }

    override fun recovery(policy: RecoveryPolicy): AOG {
        assert(recoveryEnabled(policy))
        return AOG(this, recoveryPolicy)
    }

    override val routeChanged get() = recoveryAirport != null
    override val routeChange
        get() = recoveryAirport?.let {
            RouteChange(
                Route(plan.airport, plan.airport),
                Route(it, it)
            )
        }
}
