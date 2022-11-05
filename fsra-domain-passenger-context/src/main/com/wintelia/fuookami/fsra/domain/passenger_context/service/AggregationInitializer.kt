package com.wintelia.fuookami.fsra.domain.passenger_context.service

import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.passenger_context.*
import com.wintelia.fuookami.fsra.domain.passenger_context.model.*

class AggregationInitializer {
    operator fun invoke(flightTasks: List<FlightTask>, input: Input): Result<Aggregation, Error> {
        val flights = flightTasks.filter { it.type is FlightFlightTask }
        val passengers = ArrayList<Passenger>()
        for (flight in flights) {
            val dto = input.flights.find { it.id == flight.actualId } ?: continue
            passengers.add(Passenger(
                num = dto.firstClassNum,
                flights = listOf(Pair(flight, PassengerClass.First))
            ))
            passengers.add(
                Passenger(
                num = dto.businessClassNum,
                flights = listOf(Pair(flight, PassengerClass.Business))
            )
            )
            passengers.add(
                Passenger(
                num = dto.economyClassNum,
                flights = listOf(Pair(flight, PassengerClass.Economy))
            ))
        }
        return Ok(Aggregation(flights, passengers))
    }
}
