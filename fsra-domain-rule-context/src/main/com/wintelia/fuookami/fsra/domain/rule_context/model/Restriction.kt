package com.wintelia.fuookami.fsra.domain.rule_context.model

import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.infrastructure.*

enum class RestrictionType {
    Weak,
    ViolableStrong,
    Strong,
}

sealed class RestrictionCheckingResult
object NotMatter : RestrictionCheckingResult()
object Violate : RestrictionCheckingResult()
data class ViolableViolate(val cost: Flt64) : RestrictionCheckingResult()

sealed interface Restriction {
    val type: RestrictionType

    fun related(aircraft: Aircraft): Boolean

    fun check(flightTask: FlightTask): Boolean
    fun check(flightTask: FlightTask, aircraft: Aircraft): Boolean
    fun check(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy): Boolean

    fun check(flightTask: FlightTask, parameter: Parameter): RestrictionCheckingResult
    fun check(flightTask: FlightTask, aircraft: Aircraft, parameter: Parameter): RestrictionCheckingResult
    fun check(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy, parameter: Parameter): RestrictionCheckingResult
}

enum class RelationRestrictionCategory {
    BlackList,
    WhiteList
}

class RelationRestriction(
    override val type: RestrictionType,
    val category: RelationRestrictionCategory,
    val dep: Airport,
    val arr: Airport,
    val aircrafts: Set<Aircraft>,
    val weight: Flt64 = Flt64.one,
    val cost: Flt64? = null
) : Restriction {
    override fun related(aircraft: Aircraft): Boolean {
        return aircrafts.contains(aircraft)
    }

    override fun check(flightTask: FlightTask): Boolean {
        assert(flightTask.isFlight)
        return check(flightTask.dep, flightTask.arr, flightTask.aircraft)
    }

    override fun check(flightTask: FlightTask, aircraft: Aircraft): Boolean {
        assert(flightTask.isFlight)
        return check(flightTask.dep, flightTask.arr, aircraft)
    }

    override fun check(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy): Boolean {
        val dep = recoveryPolicy.route?.dep ?: flightTask.dep
        val arr = recoveryPolicy.route?.arr ?: flightTask.arr
        val aircraft = recoveryPolicy.aircraft ?: flightTask.aircraft
        return check(dep, arr, aircraft)
    }

    override fun check(flightTask: FlightTask, parameter: Parameter): RestrictionCheckingResult {
        assert(flightTask.isFlight)
        return check(flightTask.dep, flightTask.arr, flightTask.aircraft, parameter)
    }

    override fun check(flightTask: FlightTask, aircraft: Aircraft, parameter: Parameter): RestrictionCheckingResult {
        assert(flightTask.isFlight)
        return check(flightTask.dep, flightTask.arr, aircraft, parameter)
    }

    override fun check(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy, parameter: Parameter): RestrictionCheckingResult {
        assert(flightTask.isFlight)
        val dep = recoveryPolicy.route?.dep ?: flightTask.dep
        val arr = recoveryPolicy.route?.arr ?: flightTask.arr
        val aircraft = recoveryPolicy.aircraft ?: flightTask.aircraft
        return check(dep, arr, aircraft, parameter)
    }

    private fun check(dep: Airport, arr: Airport, aircraft: Aircraft?): Boolean {
        if (dep != this.dep || arr != this.arr) {
            return true
        }
        return aircraft?.let { !violated(aircrafts.contains(aircraft)) } ?: true
    }

    private fun check(dep: Airport, arr: Airport, aircraft: Aircraft?, parameter: Parameter): RestrictionCheckingResult {
        if (dep != this.dep || arr != this.arr) {
            return NotMatter
        }
        return aircraft?.let { dump(aircrafts.contains(aircraft), parameter) } ?: NotMatter
    }

    private fun violated(hit: Boolean): Boolean {
        return when (category) {
            RelationRestrictionCategory.BlackList -> hit
            RelationRestrictionCategory.WhiteList -> !hit
        }
    }

    private fun dump(hit: Boolean, parameter: Parameter): RestrictionCheckingResult {
        return when (type) {
            RestrictionType.Weak -> {
                if (violated(hit)) {
                    if (cost != null) {
                        ViolableViolate(cost * weight)
                    } else {
                        ViolableViolate(parameter.weakRestrictionViolation * weight)
                    }
                } else {
                    ViolableViolate(Flt64.zero)
                }
            }

            RestrictionType.ViolableStrong -> {
                if (violated(hit)) {
                    if (cost != null) {
                        ViolableViolate(cost * weight)
                    } else {
                        ViolableViolate(parameter.strongRestrictionViolation * weight)
                    }
                } else {
                    ViolableViolate(Flt64.zero)
                }
            }

            RestrictionType.Strong -> {
                if (violated(hit)) {
                    if (cost != null) {
                        ViolableViolate(cost * weight)
                    } else if (parameter.inviolableStrongRestrictionViolation != null) {
                        ViolableViolate(parameter.inviolableStrongRestrictionViolation!! * weight)
                    } else {
                        Violate
                    }
                } else {
                    ViolableViolate(Flt64.zero)
                }
            }
        }
    }
}

data class GeneralRestrictionCondition(
    val time: TimeRange = TimeRange(),
    val departureAirports: Set<Airport>? = null,
    val arrivalAirports: Set<Airport>? = null,
    val bidirectional: Boolean? = null,
    val enabledAircrafts: Set<Aircraft>? = null,
    val disabledAircrafts: Set<Aircraft>? = null
) {
    fun departureValid(time: TimeRange): Boolean = this.time.contains(time.begin)
    fun arrivalValid(time: TimeRange): Boolean = this.time.contains(time.end)
    fun valid(time: TimeRange): Boolean = this.time.withIntersection(time)
}

private typealias Condition = GeneralRestrictionCondition

private sealed interface Policy {
    fun ifValid(condition: Condition): Boolean
    fun check(condition: Condition, flightTask: FlightTask, recoveryPolicy: RecoveryPolicy?): Boolean
}

private object DepartureAirportPolicy : Policy {
    override fun ifValid(condition: Condition): Boolean {
        return !BidirectionalAirportPolicy.ifValid(condition)
                && condition.departureAirports?.isNotEmpty() == true
    }

    override fun check(condition: Condition, flightTask: FlightTask, recoveryPolicy: RecoveryPolicy?): Boolean {
        assert(ArrivalAirportPolicy.ifValid(condition))
        val time = recoveryPolicy?.time ?: flightTask.time
        val dep = recoveryPolicy?.route?.dep ?: flightTask.dep
        return time != null && condition.departureValid(time) && condition.departureAirports!!.contains(dep)
    }
}

private object ArrivalAirportPolicy : Policy {
    override fun ifValid(condition: Condition): Boolean {
        return !BidirectionalAirportPolicy.ifValid(condition)
                && condition.arrivalAirports?.isNotEmpty() == true
    }

    override fun check(condition: Condition, flightTask: FlightTask, recoveryPolicy: RecoveryPolicy?): Boolean {
        assert(ifValid(condition))
        val time = recoveryPolicy?.time ?: flightTask.time
        val arr = recoveryPolicy?.route?.arr ?: flightTask.arr
        return time != null && condition.arrivalValid(time) && condition.arrivalAirports!!.contains(arr)
    }
}

private object BidirectionalAirportPolicy : Policy {
    override fun ifValid(condition: Condition): Boolean {
        return condition.departureAirports?.isNotEmpty() == true
                && condition.arrivalAirports?.isNotEmpty() == true
                && condition.bidirectional == true
    }

    override fun check(condition: Condition, flightTask: FlightTask, recoveryPolicy: RecoveryPolicy?): Boolean {
        assert(ifValid(condition))
        val time = recoveryPolicy?.time ?: flightTask.time
        val dep = recoveryPolicy?.route?.dep ?: flightTask.dep
        val arr = recoveryPolicy?.route?.arr ?: flightTask.arr
        return time != null && condition.valid(time)
                && ((condition.departureAirports!!.contains(dep) && condition.arrivalAirports!!.contains(arr))
                || (condition.arrivalAirports!!.contains(dep) && condition.departureAirports.contains(arr))
                )
    }
}

private object EnabledAircraftPolicy : Policy {
    override fun ifValid(condition: Condition): Boolean {
        return condition.enabledAircrafts?.isNotEmpty() == true
    }

    override fun check(condition: Condition, flightTask: FlightTask, recoveryPolicy: RecoveryPolicy?): Boolean {
        val time = recoveryPolicy?.time ?: flightTask.time
        val aircraft = recoveryPolicy?.aircraft ?: flightTask.aircraft
        return time != null && aircraft != null && condition.valid(time) && !condition.enabledAircrafts!!.contains(aircraft)
    }
}

private object DisabledAircraft : Policy {
    override fun ifValid(condition: Condition): Boolean {
        return condition.disabledAircrafts?.isNotEmpty() == true
    }

    override fun check(condition: Condition, flightTask: FlightTask, recoveryPolicy: RecoveryPolicy?): Boolean {
        val time = recoveryPolicy?.time ?: flightTask.time
        val aircraft = recoveryPolicy?.aircraft ?: flightTask.aircraft
        return time != null && aircraft != null && condition.valid(time) && condition.disabledAircrafts!!.contains(aircraft)
    }
}

private object PolicyFactory {
    operator fun invoke(condition: Condition): List<Policy> {
        val ret = ArrayList<Policy>()
        val addIfValid = { it: Policy ->
            if (it.ifValid(condition)) {
                ret.add(it)
            }
        }
        addIfValid(DepartureAirportPolicy)
        addIfValid(ArrivalAirportPolicy)
        addIfValid(BidirectionalAirportPolicy)
        addIfValid(EnabledAircraftPolicy)
        addIfValid(DisabledAircraft)
        return ret
    }
}

class GeneralRestriction(
    override val type: RestrictionType,
    val condition: Condition,
    val weight: Flt64 = Flt64.one,
    val cost: Flt64? = null
) : Restriction {
    private val policies = PolicyFactory(condition)

    override fun related(aircraft: Aircraft): Boolean {
        return (condition.enabledAircrafts?.contains(aircraft) ?: false)
                || (condition.disabledAircrafts?.contains(aircraft) ?: false)
    }

    override fun check(flightTask: FlightTask): Boolean {
        assert(flightTask.isFlight)
        return check(flightTask, RecoveryPolicy())
    }

    override fun check(flightTask: FlightTask, aircraft: Aircraft): Boolean {
        assert(flightTask.isFlight)
        return check(flightTask, RecoveryPolicy(aircraft = aircraft))
    }

    override fun check(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy): Boolean {
        assert(flightTask.isFlight)
        return !violated(flightTask, recoveryPolicy)
    }

    override fun check(flightTask: FlightTask, parameter: Parameter): RestrictionCheckingResult {
        assert(flightTask.isFlight)
        return check(flightTask, RecoveryPolicy(), parameter)
    }

    override fun check(flightTask: FlightTask, aircraft: Aircraft, parameter: Parameter): RestrictionCheckingResult {
        assert(flightTask.isFlight)
        return check(flightTask, RecoveryPolicy(aircraft = aircraft), parameter)
    }

    override fun check(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy, parameter: Parameter): RestrictionCheckingResult {
        assert(flightTask.isFlight)
        return dump(violated(flightTask, recoveryPolicy), parameter)
    }

    private fun violated(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy): Boolean {
        for (policy in policies) {
            if (!policy.check(condition, flightTask, recoveryPolicy)) {
                return false
            }
        }
        return true
    }

    private fun dump(violated: Boolean, parameter: Parameter): RestrictionCheckingResult {
        return if (!violated) {
            NotMatter
        } else {
            when (type) {
                RestrictionType.Weak -> {
                    if (cost != null) {
                        ViolableViolate(cost * weight)
                    } else {
                        ViolableViolate(parameter.weakRestrictionViolation * weight)
                    }
                }

                RestrictionType.ViolableStrong -> {
                    if (cost != null) {
                        ViolableViolate(cost * weight)
                    } else {
                        ViolableViolate(parameter.strongRestrictionViolation * weight)
                    }
                }

                RestrictionType.Strong -> {
                    if (cost != null) {
                        ViolableViolate(cost * weight)
                    } else if (parameter.inviolableStrongRestrictionViolation != null) {
                        ViolableViolate(parameter.inviolableStrongRestrictionViolation!! * weight)
                    } else {
                        Violate
                    }
                }
            }
        }
    }
}
