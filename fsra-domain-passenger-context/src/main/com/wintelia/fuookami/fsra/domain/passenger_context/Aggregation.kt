package com.wintelia.fuookami.fsra.domain.passenger_context

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.model.*

class Aggregation(
    val flights: List<FlightTask>,
    val passengers: List<Passenger>,
) {
    val passengerGroup: Map<FlightTask, List<PassengerFlight>>
    val passengerFlights: List<PassengerFlight>

    val passengerCancel: PassengerCancel
    val passengerClassChange: PassengerClassChange
    val passengerActualAmount: PassengerActualAmount

    init {
        val passengerFlights = ArrayList<PassengerFlight>()
        val passengerGroup = HashMap<FlightTask, MutableList<PassengerFlight>>()
        for (passenger in passengers) {
            for ((flight, cls) in passenger.flights) {
                val pf = PassengerFlight(passenger, flight)
                passengerFlights.add(pf)

                if (!passengerGroup.containsKey(flight.originTask)) {
                    passengerGroup[flight.originTask] = ArrayList()
                }
                passengerGroup[flight.originTask]!!.add(pf)
            }
        }
        this.passengerGroup = passengerGroup
        this.passengerFlights = passengerFlights

        passengerCancel = PassengerCancel(this.passengerFlights)
        passengerClassChange = PassengerClassChange(this.passengerFlights)
        passengerActualAmount = PassengerActualAmount(this.flights, this.passengerGroup, this.passengerCancel, this.passengerClassChange)
    }

    fun register(model: LinearMetaModel): Try<Error> {
        when (val ret = this.passengerCancel.register(model)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = this.passengerClassChange.register(model)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        when (val ret = this.passengerActualAmount.register(model)) {
            is Ok -> { }
            is Failed -> { return Failed(ret.error) }
        }
        return Ok(success)
    }
}
