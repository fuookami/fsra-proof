package com.wintelia.fuookami.fsra.domain.passenger_context.model

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*

class PassengerCancel(
    private val passengerFlights: List<PassengerFlight>
) {
    lateinit var pc: UIntVariable1

    fun register(model: LinearMetaModel): Try<Error> {
        if (passengerFlights.isNotEmpty()) {
            if (!this::pc.isInitialized) {
                pc = UIntVariable1("pc", Shape1(passengerFlights.size))
                for (pf in passengerFlights) {
                    pc[pf]!!.name = "${pc.name}_$pf"
                    pc[pf]!!.range.leq(pf.passenger.amount)
                }
            }
            model.addVars(pc)
        }
        return Ok(success)
    }
}
