package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import kotlin.math.*
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*

enum class FlightType {
    // 国内
    Domestic {
        override val isDomainType: Boolean get() = true
        override fun toChineseString() = CString("国内")
    },

    // 区域
    Regional {
        override fun toChineseString() = CString("区域")
    },

    // 国际
    International {
        override fun toChineseString() = CString("国际")
    };

    companion object {
        operator fun invoke(str: CString): FlightType? {
            return FlightType.values().find { it.toChineseString() == str }
        }

        operator fun invoke(dep: Airport, arr: Airport): FlightType {
            return invoke(dep.type, arr.type)
        }

        operator fun invoke(dep: AirportType, arr: AirportType): FlightType {
            return when (AirportType.values().find { it.ordinal == max(dep.ordinal, arr.ordinal) }!!) {
                AirportType.Domestic -> {
                    Domestic
                }

                AirportType.Regional -> {
                    Regional
                }

                AirportType.International -> {
                    International
                }
            }
        }
    }

    open val isDomainType: Boolean get() = false
    abstract fun toChineseString(): CString
}

class FlightPlan(
    override val actualId: String,
    val no: String,
    val type: FlightType,
    val date: Date,
    override val aircraft: Aircraft,
    override val dep: Airport,
    override val arr: Airport,
    override val scheduledTime: TimeRange,
    val estimatedTime: TimeRange?,
    val actualTime: TimeRange?,
    val outTime: Instant?,
    status: Set<FlightTaskStatus>,
    override val weight: Flt64 = Flt64.one,
) : FlightTaskPlan(
    id = "${prefix}_${actualId}",
    name = "${no}_${Date(scheduledTime.begin).toShortString()}",
    status = status
) {
    companion object {
        const val prefix = "f"
    }

    override val displayName = no

    override val time: TimeRange? get() = estimatedTime ?: super.time

    fun recoveryEnabled(): Boolean {
        return actualTime == null && outTime == null
    }
}

object FlightFlightTask : FlightTaskType(FlightTaskCategory.Flight, FlightFlightTask::class) {
    override val type get() = "flight"
}

class Flight internal constructor(
    override val plan: FlightPlan,
    val recoveryAircraft: Aircraft? = null,
    val recoveryTime: TimeRange? = null,
    origin: Flight? = null
) : FlightTask(FlightFlightTask, origin) {
    companion object {
        operator fun invoke(plan: FlightPlan): Flight {
            return Flight(plan = plan)
        }

        operator fun invoke(origin: Flight, recoveryPolicy: RecoveryPolicy): Flight {
            val recoveryAircraft = if (recoveryPolicy.aircraft == null || recoveryPolicy.aircraft == origin.aircraft) {
                null
            } else {
                recoveryPolicy.aircraft
            }
            val recoveryTime = if (recoveryPolicy.time == null || recoveryPolicy.time == origin.scheduledTime!!) {
                null
            } else {
                recoveryPolicy.time
            }

            assert(origin.recoveryEnabled(recoveryPolicy))
            return Flight(
                plan = origin.plan,
                recoveryAircraft = recoveryAircraft,
                recoveryTime = recoveryTime,
                origin = origin
            )
        }
    }

    override val aircraft get() = recoveryAircraft ?: plan.aircraft
    override val time get() = recoveryTime ?: plan.time

    override fun recoveryEnabled(timeWindow: TimeRange): Boolean {
        return plan.recoveryEnabled() && super.recoveryEnabled(timeWindow)
    }

    override fun recoveryNeeded(timeWindow: TimeRange): Boolean {
        return plan.recoveryEnabled() && timeWindow.contains(time!!.begin)
    }

    override val recovered get() = recoveryAircraft != null || recoveryTime != null
    override val recoveryPolicy get() = RecoveryPolicy(recoveryAircraft, recoveryTime, null)
    override fun recoveryEnabled(policy: RecoveryPolicy): Boolean {
        if (!aircraftChangeEnabled && policy.aircraft != null && aircraft != policy.aircraft) {
            return false
        }
        if (!aircraftTypeChangeEnabled && policy.aircraft != null && aircraft.type != policy.aircraft.type) {
            return false
        }
        if (!aircraftMinorTypeChangeEnabled && policy.aircraft != null && aircraft.minorType != policy.aircraft.minorType) {
            return false
        }
        if (!delayEnabled && policy.time != null && plan.time!!.begin < policy.time.begin) {
            return false
        }
        if (!advanceEnabled && policy.time != null && plan.time!!.begin > policy.time.begin) {
            return false
        }
        return true
    }

    override fun recovery(policy: RecoveryPolicy): FlightTask {
        assert(recoveryEnabled(policy))
        return Flight(this, policy)
    }

    override val aircraftChanged: Boolean get() = recoveryAircraft != null
    override val aircraftTypeChanged: Boolean get() = recoveryAircraft?.let { it.type != plan.aircraft.type } ?: false
    override val aircraftMinorTypeChanged: Boolean
        get() = recoveryAircraft?.let { it.minorType != plan.aircraft.minorType } ?: false
    override val aircraftChange: AircraftChange? get() = recoveryAircraft?.let { AircraftChange(plan.aircraft, it) }
    override val aircraftTypeChange: AircraftTypeChange?
        get() = recoveryAircraft?.let {
            if (it.type == plan.aircraft.type) {
                null
            } else {
                AircraftTypeChange(plan.aircraft.type, it.type)
            }
        }
    override val aircraftMinorTypeChange: AircraftMinorTypeChange?
        get() = recoveryAircraft?.let {
            if (it.minorType == plan.aircraft.minorType) {
                null
            } else {
                AircraftMinorTypeChange(plan.aircraft.minorType, it.minorType)
            }
        }

    override fun toString() = "${plan.no}, ${aircraft.regNo}, ${dep.icao} - ${arr.icao}, ${time!!.begin.toShortString()} - ${time!!.end.toShortString()}"
}
