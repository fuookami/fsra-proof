package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import java.util.*
import kotlin.time.*
import com.wintelia.fuookami.fsra.infrastructure.*

class Transfer internal constructor(
    override val dep: Airport,
    override val arr: Airport,
    val timeWindow: TimeRange,
    override val aircraft: Aircraft? = null,
    val aircrafts: Set<Aircraft> = emptySet(),
    val aircraftMinorTypes: Set<AircraftMinorType> = emptySet(),
    override val duration: Duration? = null,
    override val actualId: String = UUID.randomUUID().toString(),
    status: Set<FlightTaskStatus> = stableStatus
) : FlightTaskPlan(
    id = "${prefix}_${actualId.replace("-", "")}",
    name = "transfer_${dep}_${arr}_${timeWindow.begin.toShortString()}",
    status = status
) {
    companion object {
        private const val prefix = "tf"

        val stableStatus = setOf(
            FlightTaskStatus.NotDelay,
            FlightTaskStatus.NotAdvance,
            FlightTaskStatus.NotTerminalChange
        )

        operator fun invoke(dep: Airport, arr: Airport, timeWindow: TimeRange, duration: Duration? = null): Transfer {
            return Transfer(
                dep = dep,
                arr = arr,
                timeWindow = timeWindow,
                duration = duration,
                status = stableStatus
            )
        }

        operator fun invoke(
            dep: Airport,
            arr: Airport,
            timeWindow: TimeRange,
            aircraft: Aircraft,
            duration: Duration? = null
        ): Transfer {
            val status = stableStatus.toHashSet()
            status.addAll(
                arrayOf(
                    FlightTaskStatus.NotAircraftChange,
                    FlightTaskStatus.NotAircraftTypeChange,
                    FlightTaskStatus.NotAircraftMinorTypeChange
                )
            )
            return Transfer(
                dep = dep,
                arr = arr,
                timeWindow = timeWindow,
                aircraft = aircraft,
                duration = duration,
                status = status
            )
        }

        @JvmName("constructByAircrafts")
        operator fun invoke(
            dep: Airport,
            arr: Airport,
            timeWindow: TimeRange,
            aircrafts: Set<Aircraft>,
            duration: Duration? = null
        ): Transfer {
            return if (aircrafts.size == 1) {
                invoke(dep, arr, timeWindow, aircrafts.first(), duration)
            } else {
                Transfer(
                    dep = dep,
                    arr = arr,
                    timeWindow = timeWindow,
                    aircrafts = aircrafts,
                    duration = duration,
                    status = stableStatus
                )
            }
        }

        @JvmName("constructByAircraftMinorTypes")
        operator fun invoke(
            dep: Airport,
            arr: Airport,
            timeWindow: TimeRange,
            aircraftMinorTypes: Set<AircraftMinorType>,
            duration: Duration? = null
        ): Transfer {
            return Transfer(
                dep = dep,
                arr = arr,
                timeWindow = timeWindow,
                aircraftMinorTypes = aircraftMinorTypes,
                duration = duration,
                status = stableStatus
            )
        }
    }

    override val displayName = "transfer"
    override val scheduledTime: TimeRange? = null

    override fun duration(aircraft: Aircraft): Duration {
        return duration ?: aircraft.routeFlyTime[dep, arr] ?: aircraft.maxRouteFlyTime
    }

    fun enabled(aircraft: Aircraft): Boolean {
        return if (this.aircraft != null) {
            this.aircraft == aircraft
        } else if (aircrafts.isNotEmpty()) {
            aircrafts.contains(aircraft)
        } else if (aircraftMinorTypes.isNotEmpty()) {
            aircraftMinorTypes.contains(aircraft.minorType)
        } else {
            true
        }
    }
}

object TransferFlightFlightTask : FlightTaskType(FlightTaskCategory.Flight, TransferFlightFlightTask::class) {
    override val type = "transfer"
}

class TransferFlight internal constructor(
    override val plan: Transfer,
    val recoveryAircraft: Aircraft? = null,
    val recoveryTime: TimeRange? = null,
    origin: TransferFlight? = null
) : FlightTask(TransferFlightFlightTask, origin) {
    companion object {
        operator fun invoke(plan: Transfer): TransferFlight {
            return TransferFlight(plan = plan)
        }

        operator fun invoke(origin: TransferFlight, recoveryPolicy: RecoveryPolicy): TransferFlight {
            val recoveryAircraft =
                if (origin.plan.aircraft != null && (recoveryPolicy.aircraft == null || recoveryPolicy.aircraft == origin.plan.aircraft)) {
                    null
                } else {
                    recoveryPolicy.aircraft
                }

            assert(origin.recoveryEnabled(recoveryPolicy))
            return TransferFlight(
                plan = origin.plan,
                recoveryAircraft = recoveryAircraft,
                recoveryTime = recoveryPolicy.time!!,
                origin = origin
            )
        }
    }

    override val aircraft get() = recoveryAircraft ?: plan.aircraft
    override val time get() = recoveryTime ?: plan.time

    override val recovered get() = recoveryAircraft != null || recoveryTime != null
    override val recoveryPolicy get() = RecoveryPolicy(recoveryAircraft, recoveryTime, null)
    override fun recoveryEnabled(policy: RecoveryPolicy): Boolean {
        if (policy.aircraft != null && !plan.enabled(policy.aircraft)) {
            return false
        }
        if (policy.time == null || !plan.timeWindow.contains(policy.time)) {
            return false
        }
        return true
    }

    override fun recovery(policy: RecoveryPolicy): FlightTask {
        assert(recoveryEnabled(policy))
        return TransferFlight(this, policy)
    }
}
