package com.wintelia.fuookami.fsra.domain.rule_context.model

import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.infrastructure.*

enum class RestrictionType{
    Weak,
    ViolableStrong,
    Strong,
}

sealed class RestrictionCheckingResult
object NotMatter : RestrictionCheckingResult()
object Violate : RestrictionCheckingResult()
data class ViolableViolate(val cost: Flt64): RestrictionCheckingResult()

sealed interface Restriction {
    val type: RestrictionType

    fun check(flightTask: FlightTask, parameter: Parameter): RestrictionCheckingResult
    fun check(flightTask: FlightTask, aircraft: Aircraft, parameter: Parameter): RestrictionCheckingResult
    fun check(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy, parameter: Parameter): RestrictionCheckingResult
}

enum class RelationRestrictionCategory{
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
): Restriction {
    override fun check(flightTask: FlightTask, parameter: Parameter): RestrictionCheckingResult {
        assert(flightTask.isFlight)
        return check(flightTask.dep, flightTask.arr, flightTask.aircraft, parameter)
    }

    override fun check(flightTask: FlightTask, aircraft: Aircraft, parameter: Parameter): RestrictionCheckingResult {
        assert(flightTask.isFlight)
        return check(flightTask.dep, flightTask.arr, aircraft, parameter)
    }

    override fun check(flightTask: FlightTask, recoveryPolicy: RecoveryPolicy, parameter: Parameter): RestrictionCheckingResult {
        val dep = recoveryPolicy.route?.dep ?: flightTask.dep
        val arr = recoveryPolicy.route?.arr ?: flightTask.arr
        val aircraft = recoveryPolicy.aircraft ?: flightTask.aircraft
        return check(dep, arr, aircraft, parameter)
    }

    private fun check(dep: Airport, arr: Airport, aircraft: Aircraft?, parameter: Parameter): RestrictionCheckingResult {
        if (dep != this.dep || arr != this.arr) {
            return NotMatter
        }
        return aircraft?.let { dump(aircrafts.contains(aircraft), parameter) } ?: NotMatter
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

    private fun violated(hit: Boolean): Boolean {
        return when (category) {
            RelationRestrictionCategory.BlackList -> hit
            RelationRestrictionCategory.WhiteList -> !hit
        }
    }
}

// todo: impl general restriction
