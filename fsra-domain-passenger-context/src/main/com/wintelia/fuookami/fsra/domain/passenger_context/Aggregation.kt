package com.wintelia.fuookami.fsra.domain.passenger_context

import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.model.*

class Aggregation(
    val flights: List<FlightTask>,
    val passengers: List<Passenger>
) {
    val passengerGroup: Map<FlightTask, List<PassengerFlight>>
    val passengerFlights: List<PassengerFlight>

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
    }
}
