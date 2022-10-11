package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import java.util.*
import com.wintelia.fuookami.fsra.infrastructure.*

class AOGPlan(
    override val aircraft: Aircraft,
    private val airport: Airport,
    override val scheduledTime: TimeRange,
    override val actualId: String = UUID.randomUUID().toString()
) : FlightTaskPlan(
    id = "${prefix}_${actualId.replace("-", "")}",
    name = "${aircraft.regNo}_AOG_${scheduledTime.begin.toShortString()}",
    status = stableStatus
) {
    companion object {
        val stableStatus = setOf(
            FlightTaskStatus.NotCancel,
            FlightTaskStatus.NotDelay,
            FlightTaskStatus.NotAdvance,
            FlightTaskStatus.NotAircraftChange,
            FlightTaskStatus.NotAircraftTypeChange,
            FlightTaskStatus.NotAircraftMinorTypeChange,
            FlightTaskStatus.NotTerminalChange
        )

        private const val prefix = "a"
    }

    override val displayName = "AOG"
    override val dep = airport
    override val arr = airport

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
    origin: AOG? = null
) : FlightTask(AOGFlightTask, origin) {
    companion object {
        operator fun invoke(plan: AOGPlan) = AOG(plan)
    }

    override fun recoveryEnabled(timeWindow: TimeRange) = true
    override fun recoveryNeeded(timeWindow: TimeRange) = true

    override val recovered get() = false
    override val recoveryPolicy get() = RecoveryPolicy()
    override fun recoveryEnabled(policy: RecoveryPolicy): Boolean {
        if (policy.aircraft != null && aircraft!! != policy.aircraft) {
            return false
        }
        if (policy.time != null && time != policy.time) {
            return false
        }
        if (policy.route != null && (dep != policy.route.dep || arr != policy.route.arr)) {
            return false
        }
        return super.recoveryEnabled(policy)
    }

    override fun recovery(policy: RecoveryPolicy): AOG {
        assert(recoveryEnabled(policy))
        return this
    }
}
