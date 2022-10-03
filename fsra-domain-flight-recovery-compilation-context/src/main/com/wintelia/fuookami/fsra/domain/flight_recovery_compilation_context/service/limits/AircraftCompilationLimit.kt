package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service.limits

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.inequality.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import fuookami.ospf.kotlin.framework.model.Extractor
import fuookami.ospf.kotlin.framework.model.ShadowPrice
import fuookami.ospf.kotlin.framework.model.ShadowPriceKey
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model.*

private data class AircraftCompilationShadowPriceKey(
    val aircraft: Aircraft
) : ShadowPriceKey(AircraftCompilationShadowPriceKey::class)

class AircraftCompilationLimit(
    val aircrafts: List<Aircraft>,
    val compilation: Compilation,
    val parameter: Parameter,
    override val name: String = "aircraft_compilation"
) : CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val compilation = this.compilation.aircraftCompilation
        val z = this.compilation.z

        for (aircraft in aircrafts) {
            model.addConstraint(
                compilation[aircraft]!! + z[aircraft]!! eq UInt64.one,
                "${name}_${aircraft.regNo}"
            )
        }

        val obj = LinearPolynomial()
        for (aircraft in aircrafts) {
            obj += parameter.aircraftLeisure * z[aircraft]!!
        }
        model.minimize(obj, "aircraft leisure")

        return Ok(success)
    }

    override fun extractor(): Extractor<ShadowPriceMap> {
        return { map, args ->
            if (args[2] != null && args[0] == null && args[1] == null) {
                map[AircraftCompilationShadowPriceKey(args[2] as Aircraft)]?.price ?: Flt64.zero
            } else {
                Flt64.zero
            }
        }
    }

    override fun refresh(map: ShadowPriceMap, model: LinearMetaModel, shadowPrices: List<Flt64>): Try<Error> {
        var i = 0
        for (j in model.constraints.indices) {
            val constraint = model.constraints[j]
            if (constraint.name.startsWith(name)) {
                map.put(
                    ShadowPrice(
                        key = AircraftCompilationShadowPriceKey(aircrafts[i]),
                        price = shadowPrices[j]
                    )
                )
                ++i
            }

            if (i == aircrafts.size) {
                break
            }
        }

        return Ok(success)
    }
}
