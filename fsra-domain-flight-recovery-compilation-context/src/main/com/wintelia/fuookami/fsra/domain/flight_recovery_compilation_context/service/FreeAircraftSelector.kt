package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.limits.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class FreeAircraftSelector(
    private val aggregation: Aggregation,
    private val configuration: FreeAircraftSelectorConfiguration
) {
    private val tabuAircrafts: MutableList<Aircraft> = ArrayList()

    operator fun invoke(fixedBunches: Set<FlightTaskBunch>, hiddenAircrafts: Set<Aircraft>, shadowPriceMap: ShadowPriceMap, model: LinearMetaModel): Result<Set<Aircraft>, Error> {
        val bunches = fixedBunches.toMutableList()
        val freeAircrafts = hiddenAircrafts.toMutableSet()

        when (val ret = freeBadReducedCostAircrafts(bunches, freeAircrafts, shadowPriceMap, model)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = freeAirportCloseAircrafts(bunches, freeAircrafts)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = freeNoBusyAircrafts(bunches, freeAircrafts)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = freeHighCostAircrafts(bunches, freeAircrafts)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = freeHighCostDensityAircrafts(bunches, freeAircrafts)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = freeHighFlowControlCostAircrafts(bunches, freeAircrafts, shadowPriceMap)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = freeHighDelayAircrafts(bunches, freeAircrafts)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = freeHighAircraftChangeAircrafts(bunches, freeAircrafts)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = freeRandAircrafts(bunches, freeAircrafts)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }

        return Ok(freeAircrafts)
    }

    private fun freeBadReducedCostAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>, shadowPriceMap: ShadowPriceMap, model: LinearMetaModel): Try<Error> {
        val values = tidyValue(fixedBunches, model)
        val value = { it: FlightTaskBunch -> shadowPriceMap.reducedCost(it) - it.cost.sum!! * (values[it] ?: Flt64.zero) }
        fixedBunches.sortByDescending { value(it) }
        var amount = UInt64.zero
        for (bunch in fixedBunches) {
            val aircraft = bunch.aircraft
            if (!freeAircrafts.contains(aircraft) && aircraft.indexed && gr(value(bunch), configuration.fixBar)) {
                ++amount
                freeAircrafts.add(aircraft)
            }

            if (amount == configuration.badReducedAmount) {
                break
            }
        }
        return Ok(success)
    }

    private fun freeAirportCloseAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>): Try<Error> {
        for (bunch in fixedBunches) {
            val aircraft = bunch.aircraft
            if (!freeAircrafts.contains(aircraft) && aircraft.indexed && aggregation.flow.checkPoints.any {
                    it.closed && (it(bunch) != UInt64.zero)
                }) {
                freeAircrafts.add(bunch.aircraft)
            }
        }
        return Ok(success)
    }

    private fun freeNoBusyAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>): Try<Error> {
        fixedBunches.sortBy { it.busyTime }
        return freeAircrafts(fixedBunches, freeAircrafts, configuration.noBusyAmount)
    }

    private fun freeHighCostAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>): Try<Error> {
        fixedBunches.sortByDescending { it.cost.sum }
        return freeAircrafts(fixedBunches, freeAircrafts, configuration.highCostAmount)
    }

    private fun freeHighCostDensityAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>): Try<Error> {
        fixedBunches.sortByDescending { it.costDensity }
        return freeAircrafts(fixedBunches, freeAircrafts, configuration.highCostDensityAmount)
    }

    private fun freeHighFlowControlCostAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>, shadowPriceMap: ShadowPriceMap): Try<Error> {
        val value = tidyFlowControlCost(fixedBunches, shadowPriceMap)
        fixedBunches.sortBy { value[it] }
        return freeAircrafts(fixedBunches, freeAircrafts, configuration.highFlowControlCostAmount)
    }

    private fun freeHighDelayAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>): Try<Error> {
        fixedBunches.sortByDescending { it.totalDelay }
        return freeAircrafts(fixedBunches, freeAircrafts, configuration.highDelayAmount)
    }

    private fun freeHighAircraftChangeAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>): Try<Error> {
        fixedBunches.sortByDescending { it.aircraftChange }
        return freeAircrafts(fixedBunches, freeAircrafts, configuration.highAircraftChangeAmount)
    }

    private fun freeRandAircrafts(fixedBunches: MutableList<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>): Try<Error> {
        if (fixedBunches.isEmpty()) {
            return Ok(success)
        }

        fixedBunches.shuffle()
        var amount = UInt64.zero
        for (i in 0 until 10090) {
            val bunch = fixedBunches[fixedBunches.indices.random()]
            val aircraft = bunch.aircraft
            if (!freeAircrafts.contains(aircraft) && aircraft.indexed && amount != configuration.randAmount && !tabu(aircraft)) {
                ++amount
                freeAircrafts.add(aircraft)
                taboo(aircraft)
            }
            if (amount == configuration.randAmount) {
                break
            }
        }
        return Ok(success)
    }

    private fun freeAircrafts(fixedBunches: List<FlightTaskBunch>, freeAircrafts: MutableSet<Aircraft>, maximumAmount: UInt64): Try<Error> {
        var amount = UInt64.zero
        for (bunch in fixedBunches) {
            val aircraft = bunch.aircraft
            if (!freeAircrafts.contains(aircraft) && aircraft.indexed && amount != configuration.randAmount && !tabu(aircraft)) {
                ++amount
                freeAircrafts.add(aircraft)
                taboo(aircraft)
            }
            if (amount == configuration.randAmount) {
                break
            }
        }
        return Ok(success)
    }

    private fun tidyValue(fixedBunches: List<FlightTaskBunch>, model: LinearMetaModel): Map<FlightTaskBunch, Flt64> {
        val ret = HashMap<FlightTaskBunch, Flt64>()
        for (bunch in fixedBunches) {
            val x = aggregation.compilation.x[bunch.iteration.toInt()][bunch]!!
            val token = model.tokens.token(x)
            if (token == null) {
                ret[bunch] = Flt64.zero
            } else {
                ret[bunch] = token.result!!
            }
        }
        return ret
    }

    private fun tidyFlowControlCost(fixedBunches: List<FlightTaskBunch>, shadowPriceMap: ShadowPriceMap): Map<FlightTaskBunch, Flt64> {
        val ret = HashMap<FlightTaskBunch, Flt64>()
        for (bunch in fixedBunches) {
            var value = Flt64.zero
            for (checkPoint in aggregation.flow.checkPoints) {
                value += shadowPriceMap.map[FlowControlShadowPriceKey(checkPoint)]?.price ?: Flt64.zero
            }
            ret[bunch] = value
        }
        return ret
    }

    private fun taboo(aircraft: Aircraft) {
        if (tabuAircrafts.size == configuration.tabuAmount.toInt()) {
            tabuAircrafts.removeFirst()
        }
        tabuAircrafts.add(aircraft)
    }

    private fun tabu(aircraft: Aircraft): Boolean {
        return tabuAircrafts.contains(aircraft)
    }
}
