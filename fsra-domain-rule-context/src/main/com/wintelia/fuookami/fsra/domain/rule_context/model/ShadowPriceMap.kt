package com.wintelia.fuookami.fsra.domain.rule_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.framework.model.Extractor
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

private typealias OriginShadowPriceMap<M> = fuookami.ospf.kotlin.framework.model.ShadowPriceMap<M>

fun <M : OriginShadowPriceMap<M>> wrap(extractor: (OriginShadowPriceMap<M>, FlightTask?, FlightTask?, Aircraft) -> Flt64): Extractor<M> {
    return { map, args -> extractor(map, args[0] as FlightTask?, args[1] as FlightTask?, args[2] as Aircraft) }
}

class ShadowPriceMap : OriginShadowPriceMap<ShadowPriceMap>() {
    operator fun invoke(prevFlightTask: FlightTask?, thisFlightTask: FlightTask?, aircraft: Aircraft): Flt64 {
        return super.invoke(prevFlightTask, thisFlightTask, aircraft)
    }

    fun reducedCost(bunch: FlightTaskBunch): Flt64 {
        var ret = bunch.cost.sum!!
        if (bunch.aircraft.indexed) {
            ret -= this(null, null, bunch.aircraft)
            for ((index, flightTask) in bunch.flightTasks.withIndex()) {
                val prevFlightTask = if (index != 0) {
                    bunch.flightTasks[index - 1]
                } else {
                    bunch.lastTask
                }
                ret -= this(prevFlightTask, flightTask, bunch.aircraft)
            }
            if (bunch.flightTasks.isNotEmpty()) {
                ret -= this(bunch.flightTasks.last(), null, bunch.aircraft)
            }
        }
        return ret
    }
}
