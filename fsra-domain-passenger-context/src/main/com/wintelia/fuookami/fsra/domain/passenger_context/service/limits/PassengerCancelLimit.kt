package com.wintelia.fuookami.fsra.domain.passenger_context.service.limits

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.model.*

class PassengerCancelLimit(
    private val passengerFlights: List<PassengerFlight>,
    private val passengerCancel: PassengerCancel,
    private val parameter: Parameter,
    override val name: String = "passenger_cancel"
): CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val pc = passengerCancel.pc

        val poly = LinearPolynomial()
        for (pf in passengerFlights) {
            poly += parameter.passengerCancel * pc[pf]!!
        }
        model.minimize(poly, "passenger cancel")

        return Ok(success)
    }
}
