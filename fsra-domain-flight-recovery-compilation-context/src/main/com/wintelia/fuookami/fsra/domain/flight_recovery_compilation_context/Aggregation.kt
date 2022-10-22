package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context

import org.apache.logging.log4j.kotlin.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.math.ordinary.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.FlightLink

class Aggregation(
    val recoveryNeededAircrafts: List<Aircraft>,
    val recoveryNeededFlightTasks: List<FlightTask>,

    aircraftUsability: Map<Aircraft, AircraftUsability>,
    originBunches: List<FlightTaskBunch>,
    flightLinkMap: FlightLinkMap,
    flowControls: List<FlowControl>,

    recoveryPlan: RecoveryPlan,
    configuration: Configuration
) {
    private val logger = logger()

    val bunches: MutableList<FlightTaskBunch> = ArrayList()
    val removedBunches: HashSet<FlightTaskBunch> = HashSet()
    val bunchGroups: MutableList<List<FlightTaskBunch>> = ArrayList()
    fun bunches(iteration: UInt64): List<FlightTaskBunch> {
        return bunchGroups[iteration.toInt()]
    }

    val compilation: Compilation = Compilation()
    lateinit var flightTaskTime: FlightTaskTime
    val flightCapacity: FlightCapacity
    val flightLink: FlightLink
    val flow: Flow
    val fleetBalance: FleetBalance

    init {
        if (configuration.flightTaskTimeNeeded) {
            flightTaskTime = FlightTaskTime(configuration.withRedundancy)
        }
        flightCapacity = FlightCapacity(withPassenger = configuration.withPassenger, withCargo = configuration.withCargo)
        flightLink = FlightLink(flightLinkMap.linkPairs)
        flow = Flow(flowControls, recoveryPlan)
        fleetBalance = FleetBalance(recoveryNeededAircrafts, originBunches, aircraftUsability)
    }

    fun register(lock: Lock, model: LinearMetaModel, configuration: Configuration): Try<Error> {
        when (val ret = compilation.register(recoveryNeededFlightTasks, recoveryNeededAircrafts, lock, model)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        if (configuration.flightTaskTimeNeeded) {
            when (val ret = flightTaskTime.register(recoveryNeededFlightTasks, model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCargo || configuration.withPassenger) {
            when (val ret = flightCapacity.register(recoveryNeededFlightTasks, model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        when (val ret = flightLink.register(model)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret = flow.register(model)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret = fleetBalance.register(compilation, model)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        return Ok(success)
    }

    fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>, timeWindow: TimeRange, model: LinearMetaModel, configuration: Configuration): Try<Error> {
        this.bunches.addAll(bunches)
        bunchGroups.add(bunches)

        when (val ret = compilation.addColumns(iteration, bunches, recoveryNeededFlightTasks, recoveryNeededAircrafts, model)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        if (configuration.flightTaskTimeNeeded) {
            when (val ret = flightTaskTime.addColumns(iteration, bunches, recoveryNeededFlightTasks, timeWindow, compilation)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        when (val ret = flightCapacity.addColumns(iteration, bunches, recoveryNeededFlightTasks, compilation)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret = flightLink.addColumns(iteration, bunches, compilation)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret = flow.addColumns(iteration, bunches, compilation)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret = fleetBalance.addColumns(iteration, bunches, compilation)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        return Ok(success)
    }

    fun removeColumns(
        maximumReducedCost: Flt64,
        maximumColumnAmount: UInt64,
        shadowPriceMap: ShadowPriceMap,
        fixedBunches: Set<FlightTaskBunch>,
        keptBunches: Set<FlightTaskBunch>,
        model: LinearMetaModel
    ): Result<Flt64, Error> {
        for (bunch in bunches) {
            if (removedBunches.contains(bunch)) {
                continue
            }

            val reducedCost = shadowPriceMap.reducedCost(bunch)
            if (reducedCost ls maximumReducedCost
                && !fixedBunches.contains(bunch)
                && !keptBunches.contains(bunch)
            ) {
                removedBunches.add(bunch)
            }
        }

        for (bunch in removedBunches) {
            val xi = compilation.x[bunch.iteration.toInt()]
            xi[bunch]!!.range.eq(UInt8.zero)
            model.remove(xi[bunch]!!)
        }

        val remainingAmount = UInt64((bunches.size - removedBunches.size).toULong())
        return if (remainingAmount > maximumColumnAmount) {
            Ok(max((maximumReducedCost.floor().toInt64() * Int64(2L) / Int64(3L)).toFlt64(), Flt64(5.0)))
        } else {
            Ok(maximumReducedCost)
        }
    }

    fun extractFixedBunches(iteration: UInt64, model: LinearMetaModel): Result<Set<FlightTaskBunch>, Error> {
        return extractBunches(iteration, model) { eq(it, Flt64.one) }
    }

    fun extractKeptFlightBunches(iteration: UInt64, model: LinearMetaModel): Result<Set<FlightTaskBunch>, Error> {
        return extractBunches(iteration, model) { gr(it, Flt64.zero) }
    }

    fun extractHiddenAircrafts(model: LinearMetaModel): Result<Set<Aircraft>, Error> {
        val z = compilation.z
        val ret = HashSet<Aircraft>()
        for (token in model.tokens.tokens) {
            if (token.variable.identifier == z.identifier) {
                if (gr(token.result!!, Flt64.zero)) {
                    ret.add(recoveryNeededAircrafts[token.variable.index])
                }
            }
        }
        return Ok(ret)
    }

    fun globallyFix(fixedBunches: Set<FlightTaskBunch>): Try<Error> {
        for (bunch in fixedBunches) {
            assert(!removedBunches.contains(bunch))
            val xi = compilation.x[bunch.iteration.toInt()]
            xi[bunch]!!.range.eq(UInt8.one)
        }
        return Ok(success)
    }

    fun locallyFix(iteration: UInt64, bar: Flt64, fixedBunches: Set<FlightTaskBunch>, model: LinearMetaModel): Result<Set<FlightTaskBunch>, Error> {
        var flag = true
        val ret = HashSet<FlightTaskBunch>()

        var bestValue = Flt64.zero

        val y = compilation.y
        val z = compilation.z
        for (token in model.tokens.tokens) {
            if (token.variable.identifier == y.identifier
                && gr(token.result!!, bar)
            ) {
                y[token.variable.index]!!.range.eq(UInt8.one)
                flag = false
            }

            if (token.name.startsWith("x")) {
                for (i in UInt64.zero..iteration) {
                    val xi = compilation.x[i.toInt()]

                    if (token.variable.identifier == xi.identifier) {
                        val bunch = bunches(i)[token.variable.index]
                        assert(!removedBunches.contains(bunch))

                        if (token.result != null && geq(token.result!!, bestValue) && !fixedBunches.contains(bunch)) {
                            bestValue = token.result!!
                        }
                        if (token.result != null && geq(token.result!!, bar) && !fixedBunches.contains(bunch)) {
                            ret.add(bunch)
                            xi[token.variable.index]!!.range.eq(UInt8.one)
                        }
                    }
                }
            }
        }

        // if not fix any one bunch or cancel any flight
        // fix the best if the value greater than 1e-3
        if (flag && ret.isEmpty() && geq(bestValue, Flt64(1e-3))) {
            for ((i, xi) in compilation.x.withIndex()) {
                for (x in xi) {
                    if (eq(model.tokens.token(x!!)!!.result!!, bestValue)) {
                        ret.add(bunches(UInt64(i.toULong()))[x.index])
                        x.range.eq(UInt8.one)
                    }
                }
            }
        }

        return Ok(ret)
    }

    fun logResult(iteration: UInt64, model: LinearMetaModel): Try<Error> {
        val y = compilation.y
        val z = compilation.z

        for (token in model.tokens.tokens) {
            if (gr(token.result!!, Flt64.zero)) {
                logger.debug { "${token.name} = ${token.result!!}" }
            }
        }

        for (obj in model.subObjects) {
            logger.debug { "${obj.name} = ${obj.value()}"}
        }

        return Ok(success)
    }

    fun logBunchCost(iteration: UInt64, model: LinearMetaModel): Try<Error> {
        for (token in model.tokens.tokens) {
            if (eq(token.result!!, Flt64.one) && token.name.startsWith("x")) {
                for (i in UInt64.zero..iteration) {
                    val xi = compilation.x[i.toInt()]

                    if (token.variable.identifier == xi.identifier) {
                        val bunch = bunches(i)[token.variable.index]
                        logger.debug { "${bunch.aircraft.regNo} cost: ${bunch.cost.sum!!}" }
                        break
                    }
                }
            }
        }

        return Ok(success)
    }

    fun flush(iteration: UInt64, lock: Lock): Try<Error> {
        val y = compilation.y
        for (flightTask in recoveryNeededFlightTasks) {
            if (flightTask.cancelEnabled && !lock.lockedCancelFlightTasks.contains(flightTask.key)) {
                y[flightTask]!!.range.set(ValueRange(Binary.minimum, Binary.maximum, UInt8))
            }

            for (i in UInt64.zero..iteration) {
                val xi = compilation.x[i.toInt()]

                for (bunch in bunches(i)) {
                    if (!removedBunches.contains(bunch)) {
                        xi[bunch]!!.range.set(ValueRange(Binary.minimum, Binary.maximum, UInt8))
                    }
                }
            }
        }
        return Ok(success)
    }

    private fun extractBunches(iteration: UInt64, model: LinearMetaModel, predicate: (Flt64) -> Boolean): Result<Set<FlightTaskBunch>, Error> {
        val ret = HashSet<FlightTaskBunch>()
        for (token in model.tokens.tokens) {
            if (!predicate(token.result!!)) {
                continue
            }

            for (i in 0..iteration.toInt()) {
                val xi = compilation.x[i]

                if (token.variable.identifier == xi.identifier) {
                    val bunch = bunchGroups[i][token.variable.index]
                    assert(!removedBunches.contains(bunch))
                    ret.add(bunch)
                }
            }
        }
        return Ok(ret)
    }
}
