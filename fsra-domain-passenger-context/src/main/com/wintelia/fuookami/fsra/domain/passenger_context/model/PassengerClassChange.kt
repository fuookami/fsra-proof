package com.wintelia.fuookami.fsra.domain.passenger_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class PassengerClassChange(
    private val passengerFlights: List<PassengerFlight>
) {
    lateinit var pcc: UIntVariable2

    fun register(model: LinearMetaModel): Try<Error> {
        if (passengerFlights.isNotEmpty()) {
            if (!this::pcc.isInitialized) {
                pcc = UIntVariable2("pcc", Shape2(passengerFlights.size, PassengerClass.values().size))
                for (pf in passengerFlights) {
                    for (cls in PassengerClass.values()) {
                        val variable = pcc[pf, cls]!!
                        variable.name = "${pcc.name}_${pf}_${cls}"
                        if (cls.ordinal <= pf.cls.ordinal) {
                            variable.range.eq(UInt64.zero)
                        } else {
                            variable.range.leq(pf.passenger.num)
                        }
                    }
                }
            }
            for (pf in passengerFlights) {
                for (cls in PassengerClass.values()) {
                    if (cls.ordinal > pf.cls.ordinal) {
                        model.addVar(pcc[pf, cls]!!)
                    }
                }
            }
        }
        return Ok(success)
    }
}
