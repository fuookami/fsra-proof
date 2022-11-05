package com.wintelia.fuookami.fsra.domain.passenger_context.service.limits

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.framework.model.CGPipeline
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.PassengerClass
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.model.*

class PassengerClassChangeLimit(
    private val passengerFlights: List<PassengerFlight>,
    private val passengerClassChange: PassengerClassChange,
    private val parameter: Parameter,
    override val name: String = "passenger_class_change"
): CGPipeline<LinearMetaModel, ShadowPriceMap> {
    override fun invoke(model: LinearMetaModel): Try<Error> {
        val pcc = passengerClassChange.pcc

        val poly = LinearPolynomial()
        for (pf in passengerFlights) {
            for (cls in PassengerClass.values()) {
                if (cls.ordinal > pf.cls.ordinal) {
                    val cost = parameter.passengerClassChange.find { PassengerClass(it.first) == cls && PassengerClass(it.second) == pf.cls }?.third
                    if (cost != null) {
                        poly += cost * pcc[pf, cls]!!
                    }
                }
            }
        }
        model.minimize(poly, "passenger class change")

        return Ok(success)
    }
}
