package com.wintelia.fuookami.fsra.domain.rule_context.service

import kotlin.time.*
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class CostCalculator(
    val aggregation: Aggregation,
    val recoveryPlan: RecoveryPlan,
    val connectionTimeCalculator: ConnectionTimeCalculator,
    val minimumDepartureTimeCalculator: MinimumDepartureTimeCalculator,
    val aircraftUsability: Map<Aircraft, AircraftUsability>,
    val parameter: Parameter
) {
    private val beginDate = Date(recoveryPlan.timeWindow.begin)
    private val aircraftTypeChangeCost: Map<AircraftMinorTypeChange, Flt64>

    init {
        val aircraftTypeChangeCost = HashMap<AircraftMinorTypeChange, Flt64>()
        for ((type1, type2, cost) in parameter.aircraftTypeChange) {
            val from = AircraftMinorType(type1) ?: continue
            val to = AircraftMinorType(type2) ?: continue
            aircraftTypeChangeCost[AircraftMinorTypeChange(from, to)] = cost
        }
        for (type1 in AircraftMinorType.values) {
            for (type2 in AircraftMinorType.values) {
                if (type1 == type2) {
                    continue
                }
                val key = AircraftMinorTypeChange(type1, type2)
                if (!aircraftTypeChangeCost.containsKey(key)) {
                    aircraftTypeChangeCost[key] = parameter.aircraftTypeChangeBase
                }
            }
        }
        this.aircraftTypeChangeCost = aircraftTypeChangeCost
    }

    operator fun invoke(bunch: FlightTaskBunch): Cost? {
        return this(bunch.aircraft, bunch.flightTasks, bunch.lastTask)
    }

    operator fun invoke(aircraft: Aircraft, flightTasks: List<FlightTask>, lastFightTask: FlightTask? = null): Cost? {
        val cost = Cost()
        for (i in flightTasks.indices) {
            val prevFlightTask = if (i == 0) {
                lastFightTask
            } else {
                flightTasks[i - 1]
            }

            cost += this(aircraft, prevFlightTask, flightTasks[i]) ?: return null
            if (!cost.valid) {
                return cost
            }
        }

        for (period in aircraftUsability[aircraft]!!.flightCyclePeriods) {
            cost += overFlightHourCost(
                aircraft,
                period.expirationTime - 1.minutes,
                FlightHour(flightTasks.sumOf { it.flightHour(aircraft, period.expirationTime).hours.toInt(DurationUnit.MINUTES) }.minutes)
            )
            if (!cost.valid) {
                return cost
            }
            cost += overFlightCycleCost(
                aircraft,
                period.expirationTime - 1.minutes,
                FlightCycle(UInt64(flightTasks.sumOf { it.flightCycle(period.expirationTime).cycles.toInt() }.toULong()))
            )
        }

        return cost
    }

    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask): Cost? {
        val calculators = arrayListOf(
            { flyTimeCost(aircraft, flightTask) },
            { delayCost(prevFlightTask, flightTask) },
            { overMaxDelayCost(prevFlightTask, flightTask) },
            { advanceCost(flightTask) },
            { aircraftChangeCost(flightTask) },
            { routeChangeCost(flightTask) },
            { restrictionCost(flightTask) },
            { lockCost(prevFlightTask, flightTask) },
            { transferCost(flightTask) },
            { straightenCost(flightTask) }
        )
        if (prevFlightTask == null) {
            calculators.add { aircraftUsabilityCost(flightTask) }
        } else {
            calculators.add { airportConnectionCost(prevFlightTask, flightTask) }
            calculators.add { timeConnectionCost(prevFlightTask, flightTask) }
            calculators.add { linkSplitCost(prevFlightTask, flightTask) }
            calculators.add { orderChangeCost(prevFlightTask, flightTask) }
        }

        val cost = Cost()
        for (calc in calculators) {
            cost += calc()
            if (!cost.valid) {
                return null
            }
        }
        return cost
    }

    operator fun invoke(aircraft: Aircraft, prevFlightTask: FlightTask?, flightTask: FlightTask, prevFlightHour: FlightHour, prevFlightCycle: FlightCycle): Cost? {
        val cost = Cost()
        cost += this(aircraft, prevFlightTask, flightTask) ?: return null
        if (!cost.valid) {
            return null
        }

        cost += additionalOverFlightHourCost(aircraft, flightTask.time!!.begin, prevFlightHour, flightTask.flightHour(aircraft))
        if (!cost.valid) {
            return null
        }
        cost += additionalOverFlightCycleCost(aircraft, flightTask.time!!.begin, prevFlightCycle, flightTask.flightCycle)
        if (!cost.valid) {
            return null
        }
        return cost
    }

    fun flyTimeCost(aircraft: Aircraft, flightTask: FlightTask): CostItem {
        return if (flightTask.isFlight) {
            CostItem("fly time", aircraft.costPerHour * Flt64(flightTask.flightHour!!.hours.toDouble(DurationUnit.HOURS)))
        } else {
            CostItem("fly time", Flt64.zero)
        }
    }

    fun cancelCost(flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        if (!aggregation.lock.lockedCancelFlightTasks.contains(flightTask.key)) {
            cost += when (flightTask.type) {
                is FlightFlightTask -> {
                    flightTask.weight * if (!flightTask.cancelEnabled) {
                        parameter.recoveryLockCancel
                    } else if (flightTask.notCancelPreferred) {
                        parameter.keyFlightCancel
                    } else {
                        parameter.flightCancel
                    }
                }

                is MaintenanceFlightTask -> {
                    if (!flightTask.cancelEnabled) {
                        parameter.recoveryLockCancel
                    } else {
                        parameter.maintenanceCancel
                    }
                }

                is AOGFlightTask -> {
                    parameter.AOGCancel
                }
                // todo: if implement additional flight task
                // todo: if implement straighten flight task
                else -> {
                    Flt64.zero
                }
            }
        }
        return CostItem("cancel", cost)
    }

    fun delayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        val delay = flightTask.delay
        if (delay != Duration.ZERO
        // todo: check if it is not locked with time
        ) {
            cost = when (flightTask.type) {
                is FlightFlightTask -> {
                    val costFlight = flightTask.weight * if (!flightTask.delayEnabled) {
                        parameter.keyFlightDelay
                    } else {
                        Flt64.zero
                    }
                    val delayHours = Flt64(delay.toDouble(DurationUnit.HOURS))
                    var costHour = flightTask.weight * delayHours * parameter.flightDelayPerHour
                    // todo: check if it is locked with time
                    costHour += flightTask.weight * if (delayHours leq Flt64.one) {
                        parameter.flightDelay1H
                    } else if (delayHours leq Flt64(4.0)) {
                        parameter.flightDelay4H
                    } else if (delayHours leq Flt64(8.0)) {
                        parameter.flightDelay8H
                    } else {
                        parameter.flightDelayOver8H
                    }
                    costFlight + costHour
                }

                is MaintenanceFlightTask -> {
                    if (!flightTask.delayEnabled) {
                        parameter.recoveryLock
                    } else {
                        parameter.maintenanceDelay
                    }
                }

                is AOGFlightTask -> {
                    parameter.AOGDelay
                }

                is TransferFlightFlightTask -> {
                    return CostItem("delay", null, "transfer: ${flightTask.name}")
                }
                // todo: if implement additional flight task
                // todo: if implement straighten flight task
                else -> {
                    Flt64.zero
                }
            }
        }
        return CostItem("delay", cost)
    }

    fun overMaxDelayCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem {
        return if (flightTask.overMaxDelay > Duration.ZERO) {
            CostItem("over max delay", parameter.overMaxDelay)
        } else {
            CostItem("over max delay", Flt64.zero)
        }
    }

    fun advanceCost(flightTask: FlightTask): CostItem {
        return if (!flightTask.advanceEnabled && flightTask.advance != Duration.ZERO) {
            CostItem("advance", null)
        } else if (flightTask.advance != Duration.ZERO) {
            CostItem("advance", flightTask.weight * parameter.flightAdvancePerFlight)
        } else {
            CostItem("advance", Flt64.zero)
        }
    }

    fun aircraftChangeCost(flightTask: FlightTask): CostItem {
        if (!flightTask.aircraftChangeEnabled && flightTask.aircraftChanged) {
            // or a very big cost
            return CostItem("aircraft change", null)
        }

        var cost = Flt64.zero
        val aircraftChange = flightTask.aircraftChange
        if (aircraftChange != null) {
            // todo: if implement recovery lock, check if it is not locked
            cost += parameter.aircraftChange
        }
        val aircraftMinorTypeChange = flightTask.aircraftMinorTypeChange
        if (aircraftMinorTypeChange != null) {
            // todo: if implement recovery lock, check if it is not locked
            cost += aircraftTypeChangeCost[aircraftMinorTypeChange] ?: parameter.aircraftTypeChangeBase
        }

        val flightDate = Date(flightTask.time!!.begin)
        cost *= flightTask.weight * when ((flightDate.value - beginDate.value).toInt(DurationUnit.DAYS)) {
            0 -> parameter.aircraftChangeIntraDay
            1 -> parameter.aircraftChangeNextDay
            else -> parameter.aircraftChangeTertianDay
        }
        return CostItem("aircraft change", cost)
    }

    fun routeChangeCost(flightTask: FlightTask): CostItem {
        if (!flightTask.routeChangeEnabled && flightTask.routeChanged) {
            return CostItem("route change", null)
        }

        var cost = Flt64.zero
        val routeChange = flightTask.routeChange
        if (routeChange != null && flightTask.type is MaintenanceFlightTask) {
            cost += parameter.maintenanceAirportChange
        }
        return CostItem("route change", cost)
    }

    fun restrictionCost(flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        if (!flightTask.strongLimitIgnored) {
            for (restriction in aggregation.restrictions(flightTask.aircraft!!)) {
                when (val ret = restriction.check(flightTask, parameter)) {
                    is NotMatter -> {}
                    is Violate -> {
                        return CostItem("restriction", null)
                    }

                    is ViolableViolate -> {
                        cost += flightTask.weight * ret.cost
                    }
                }
            }
        }
        return CostItem("restriction", cost)
    }

    fun lockCost(prevFlightTask: FlightTask?, flightTask: FlightTask): CostItem {
        // todo: if implement recovery lock
        return CostItem("lock", Flt64.zero)
    }

    fun transferCost(flightTask: FlightTask): CostItem {
        return if (flightTask.type is TransferFlightFlightTask) {
            CostItem("transfer", parameter.transferFlight)
        } else {
            CostItem("transfer", Flt64.zero)
        }
    }

    fun straightenCost(flightTask: FlightTask): CostItem {
        // todo: if implement straighten flight
        return CostItem("straighten", Flt64.zero)
    }

    fun aircraftUsabilityCost(flightTask: FlightTask): CostItem {
        val location = aircraftUsability[flightTask.aircraft!!]!!.location
        val enabledTime = aircraftUsability[flightTask.aircraft!!]!!.enabledTime
        return if (flightTask.dep != location || flightTask.time!!.begin < enabledTime) {
            CostItem(
                "aircraft, usability", null,
                "${flightTask.aircraft!!.regNo}-${flightTask.name}-${flightTask.dep.icao}-${location.icao}-${flightTask.time!!.begin}-${enabledTime}"
            )
        } else {
            CostItem("aircraft usability", Flt64.zero)
        }
    }

    fun airportConnectionCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem {
        return if (prevFlightTask.arr != flightTask.dep) {
            // or a very big cost
            CostItem(
                "airport connection", null,
                "${flightTask.aircraft!!.regNo}-${prevFlightTask.name}-${flightTask.name}-${prevFlightTask.arr.icao}-${flightTask.dep.icao}"
            )
        } else {
            CostItem("airport connection", Flt64.zero)
        }
    }

    fun timeConnectionCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem {
        val prevArrivalTime = prevFlightTask.time!!.end
        val connectionTime = connectionTimeCalculator(flightTask.aircraft!!, prevFlightTask, flightTask)
        val enabledTime = minimumDepartureTimeCalculator(prevArrivalTime, flightTask.aircraft!!, flightTask, connectionTime)
        return if (flightTask.time!!.begin < maxOf(prevArrivalTime, enabledTime)) {
            CostItem(
                "time connection", null,
                "${flightTask.aircraft!!.regNo}-${prevFlightTask.name}-${flightTask.name}-${(flightTask.time!!.begin - prevFlightTask.time!!.end).toInt(DurationUnit.MINUTES)}m-${
                    connectionTime.toInt(
                        DurationUnit.MINUTES
                    )
                }m"
            )
        } else {
            CostItem("time connection", Flt64.zero)
        }
    }

    fun linkSplitCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        val links = aggregation.linkMap.linksOf(prevFlightTask)
        for (link in links) {
            if (link.succTask != flightTask) {
                cost += link.splitCost
            }
        }
        return CostItem("link split", cost)
    }

    fun orderChangeCost(prevFlightTask: FlightTask, flightTask: FlightTask): CostItem {
        var cost = Flt64.zero
        val prevScheduledTime = prevFlightTask.scheduledTime
        val scheduledTime = flightTask.scheduledTime
        if (prevScheduledTime != null && scheduledTime != null && prevScheduledTime.begin > scheduledTime.begin) {
            cost += parameter.outOfOrder
        }
        return CostItem("order change", cost)
    }

    fun overFlightHourCost(aircraft: Aircraft, time: Instant, totalFlightHour: FlightHour): CostItem {
        val overFlightHour = aircraftUsability[aircraft]!!.overFlightHourTimes(time, totalFlightHour)
        return CostItem("over flight hour", parameter.aircraftCycleHour * overFlightHour.toFlt64())
    }

    fun additionalOverFlightHourCost(aircraft: Aircraft, time: Instant, flightHour: FlightHour, additionalFlightHour: FlightHour): CostItem {
        val cost1 = overFlightHourCost(aircraft, time, flightHour)
        val cost2 = overFlightHourCost(aircraft, time, flightHour + additionalFlightHour)
        return CostItem("over flight hour", parameter.aircraftCycleHour * (cost2.value!! - cost1.value!!))
    }

    fun overFlightCycleCost(aircraft: Aircraft, time: Instant, totalFlightCycle: FlightCycle): CostItem {
        val overFlightCycle = aircraftUsability[aircraft]!!.overFlightCycleTimes(time, totalFlightCycle)
        return CostItem("over flight cycle", parameter.aircraftCycleNumber * overFlightCycle.toFlt64())
    }

    fun additionalOverFlightCycleCost(aircraft: Aircraft, time: Instant, flightCycle: FlightCycle, additionalFlightCycleCost: FlightCycle): CostItem {
        val cost1 = overFlightCycleCost(aircraft, time, flightCycle)
        val cost2 = overFlightCycleCost(aircraft, time, flightCycle + additionalFlightCycleCost)
        return CostItem("over flight cycle", parameter.aircraftCycleNumber * (cost2.value!! - cost1.value!!))
    }
}
