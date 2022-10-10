package com.wintelia.fuookami.fsra.domain.rule_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.framework.model.Extractor
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

private typealias OriginShadowPriceMap<M> = fuookami.ospf.kotlin.framework.model.ShadowPriceMap<M>

fun <M : OriginShadowPriceMap<M>> wrap(extractor: (OriginShadowPriceMap<M>, FlightTask?, FlightTask?, Aircraft) -> Flt64): Extractor<M> {
    return { map, args -> extractor(map, args[0] as FlightTask, args[1] as FlightTask, args[2] as Aircraft) }
}

class ShadowPriceMap: OriginShadowPriceMap<ShadowPriceMap>() {
    operator fun invoke(prevFlightTask: FlightTask?, thisFlightTask: FlightTask?, aircraft: Aircraft): Flt64 {
        return super.invoke(arrayOf(prevFlightTask, thisFlightTask, aircraft))
    }
}
