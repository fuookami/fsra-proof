package com.wintelia.fuookami.fsra.domain.passenger_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.concept.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class Passenger(
    val id: String,
    val num: UInt64,
    val flights: List<Pair<FlightTask, PassengerClass>>
) {
    private val _flightTaskKeys = flights.associate { Pair(it.first.key, it.second) }
    private lateinit var _route: List<Airport>
    val route: List<Airport> get() {
        if (this::_route.isInitialized) {
            val route = ArrayList<Airport>()
            route.add(flights.first().first.dep)
            for (flight in flights) {
                route.add(flight.first.arr)
            }
            this._route = route
        }
        return this._route
    }

    val departure: Airport get() = this.route.first()
    val destination: Airport get() = this.route.last()
    val transfer: Boolean get() = this.route.size > 2

    init {
        assert(flights.isNotEmpty())
    }

    fun classOf(flightTask: FlightTask): PassengerClass {
        return _flightTaskKeys[flightTask.key]!!
    }

    fun contains(flightTask: FlightTask): Boolean {
        return _flightTaskKeys.containsKey(flightTask.key)
    }
}

data class PassengerFlight(
    val passenger: Passenger,
    val flight: FlightTask
): AutoIndexed(PassengerFlight::class) {
    val cls = passenger.classOf(flight)

    override fun hashCode(): Int {
        return passenger.hashCode() xor flight.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PassengerFlight) return false

        if (passenger != other.passenger) return false
        if (flight != other.flight) return false

        return true
    }

    override fun toString() = "${flight.name}_${passenger.classOf(flight).toShortString()}_${passenger.num}_${index}"
}
