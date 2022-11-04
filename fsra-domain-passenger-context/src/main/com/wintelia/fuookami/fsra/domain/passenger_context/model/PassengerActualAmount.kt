package com.wintelia.fuookami.fsra.domain.passenger_context.model

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class PassengerActualAmount(
    private val flights: List<FlightTask>,
    private val passengerGroup: Map<FlightTask, List<PassengerFlight>>,
    val cancel: PassengerCancel,
    val classChange: PassengerClassChange
) {
    lateinit var pa: LinearSymbols2

    fun register(model: LinearMetaModel): Try<Error> {
        if (passengerGroup.isNotEmpty()) {
            val pc = cancel.pc
            val pcc = classChange.pcc

            if (!this::pa.isInitialized) {
                pa = LinearSymbols2("pa", Shape2(passengerGroup.size, PassengerClass.values().size))
                for ((i, flight) in flights.withIndex()) {
                    val poly = PassengerClass.values().associateWith { LinearPolynomial() }
                    for (pf in passengerGroup[flight]!!) {
                        poly[pf.cls]!! += pf.passenger.num
                        poly[pf.cls]!! -= pc[pf]!!

                        for (cls in PassengerClass.values()) {
                            if (cls > pf.cls) {
                                poly[cls]!! += pcc[pf, cls]!!
                                poly[pf.cls]!! -= pcc[pf, cls]!!
                            }
                        }
                    }

                    for (cls in PassengerClass.values()) {
                        pa[i, cls.ordinal] = LinearSymbol(poly[cls]!!, "pa_${cls.toShortString()}_${flight.name}")
                    }
                }
                model.addSymbols(pa)
            }
        }
        return Ok(success)
    }
}
