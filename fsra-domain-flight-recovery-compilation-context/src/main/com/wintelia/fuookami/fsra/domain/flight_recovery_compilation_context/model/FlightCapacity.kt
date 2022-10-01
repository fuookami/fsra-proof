package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class FlightCapacity(
    val withPassenger: Boolean = false,
    val withCargo: Boolean = false
) {
    lateinit var passenger: Map<PassengerClass, LinearSymbols1>
    lateinit var cargo: LinearSymbols1

    fun register(flightTasks: List<FlightTask>, model: LinearMetaModel): Try<Error> {
        if (withPassenger) {
            if (!this::passenger.isInitialized) {
                passenger = PassengerClass.values().map { cls ->
                    val passengerCapacity = LinearSymbols1("${cls}_capacity", Shape1(flightTasks.size))
                    for (task in flightTasks) {
                        if (task.capacity is AircraftCapacity.Passenger) {
                            passengerCapacity[task]!!.name = "${passengerCapacity.name}_${task.name}"
                        }
                    }
                    return@map Pair(cls, passengerCapacity)
                }.toMap()
            }
            for (task in flightTasks) {
                if (task.capacity is AircraftCapacity.Passenger) {
                    for (cls in PassengerClass.values()) {
                        model.addSymbol(passenger[cls]!![task]!!)
                    }
                }
            }
        }

        if (withCargo) {
            if (!this::cargo.isInitialized) {
                cargo = LinearSymbols1("cargo_capacity", Shape1(flightTasks.size))
                for (task in flightTasks) {
                    if (task.capacity is AircraftCapacity.Cargo) {
                        cargo[task]!!.name = "${cargo.name}_${task.name}"
                    }
                }
            }
            for (task in flightTasks) {
                if (task.capacity is AircraftCapacity.Cargo) {
                    model.addSymbol(cargo[task]!!)
                }
            }
        }

        return Ok(success)
    }

    fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>, flightTasks: List<FlightTask>, compilation: Compilation): Try<Error> {
        assert(bunches.isNotEmpty())

        val xi = compilation.x[iteration.toInt()]

        if (withPassenger) {
            for (bunch in bunches) {
                for (task in flightTasks) {
                    val actualTask = bunch.get(task) ?: continue

                    when (val aircraftCapacity = actualTask.capacity) {
                        is AircraftCapacity.Passenger -> {
                            for (cls in PassengerClass.values()) {
                                val capacity = passenger[cls]!![task]!! as LinearSymbol
                                capacity.flush()
                                (capacity.polynomial as LinearPolynomial) += aircraftCapacity[cls] * xi[bunch]!!
                            }
                        }
                        else -> { }
                    }
                }
            }
        }

        if (withCargo) {
            for (bunch in bunches) {
                for (task in flightTasks) {
                    val actualTask = bunch.get(task) ?: continue

                    when (val aircraftCapacity = actualTask.capacity) {
                        is AircraftCapacity.Cargo -> {
                            val capacity = cargo[task]!! as LinearSymbol
                            capacity.flush()
                            (capacity.polynomial as LinearPolynomial) += aircraftCapacity.capacity * xi[bunch]!!
                        }
                        else -> { }
                    }
                }
            }
        }

        return Ok(success)
    }
}