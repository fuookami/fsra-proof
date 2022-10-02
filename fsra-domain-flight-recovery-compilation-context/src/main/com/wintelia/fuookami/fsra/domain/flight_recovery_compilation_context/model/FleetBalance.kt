package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.concept.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.LinearPolynomial
import fuookami.ospf.kotlin.core.frontend.model.mechanism.LinearMetaModel

class FleetBalance(
    aircrafts: List<Aircraft>,
    originFlightTaskBunch: List<FlightTaskBunch>,
    aircraftUsability: Map<Aircraft, AircraftUsability>
) {
    data class CheckPoint(
        val airport: Airport,
        val aircraftMinorType: AircraftMinorType
    ): ManualIndexed() {
        operator fun invoke(bunch: FlightTaskBunch) = bunch.aircraft.minorType == aircraftMinorType
                && bunch.arr == airport

        override fun hashCode(): Int {
            return airport.hashCode() xor aircraftMinorType.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CheckPoint

            if (airport != other.airport) return false
            if (aircraftMinorType != other.aircraftMinorType) return false

            return true
        }
    }

    data class Limit(
        internal var _amount: UInt64,
        // leisure aircrafts located in the terminal
        internal var _aircrafts: MutableList<Aircraft> = ArrayList()
    ) {
        val amount: UInt64 get() = _amount
        val aircrafts: List<Aircraft> get() = _aircrafts
    }

    val limits: Map<CheckPoint, Limit>
    lateinit var l: UIntVariable1
    lateinit var fleet: LinearSymbols1

    init {
        val balanceLimit = HashMap<CheckPoint, Limit>()
        for (aircraft in aircrafts) {
            val bunch = originFlightTaskBunch.find { it.aircraft == aircraft }
            val key = if (bunch != null && !bunch.empty) {
                CheckPoint(bunch.arr, aircraft.minorType)
            } else {
                CheckPoint(aircraftUsability[aircraft]!!.location, aircraft.minorType)
            }
            if (!balanceLimit.containsKey(key)) {
                key.setIndexed()
                balanceLimit[key] = Limit(UInt64.zero)
            }
            balanceLimit[key]!!._amount += UInt64.one
        }
        for (aircraft in aircrafts) {
            val key = CheckPoint(aircraftUsability[aircraft]!!.location, aircraft.minorType)
            if (!balanceLimit.containsKey(key)) {
                key.setIndexed()
                balanceLimit[key] = Limit(UInt64.zero)
            }
            balanceLimit[key]!!._aircrafts.add(aircraft)
        }
        limits = balanceLimit
    }

    fun register(compilation: Compilation, model: LinearMetaModel): Try<Error> {
        if (limits.isNotEmpty()) {
            if (!this::l.isInitialized) {
                l = UIntVariable1("l", Shape1(limits.size))
                for ((checkPoint, _) in limits) {
                    l[checkPoint]!!.name = "${l.name}_${checkPoint.airport.icao}_${checkPoint.aircraftMinorType.code}"
                }
            }
            model.addVars(l)

            if (!this::fleet.isInitialized) {
                val z = compilation.z
                fleet = LinearSymbols1("fleet", Shape1(limits.size))
                for ((checkPoint, limit) in limits) {
                    val poly = LinearPolynomial()
                    for (aircraft in limit.aircrafts) {
                        poly += z[aircraft]!!
                    }
                    fleet[checkPoint] = LinearSymbol(poly, "${fleet.name}_${checkPoint.airport.icao}_${checkPoint.aircraftMinorType.code}")
                }
            }
            model.addSymbols(fleet)
        }

        return Ok(success)
    }

    fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>, compilation: Compilation): Try<Error> {
        assert(bunches.isNotEmpty())

        val xi = compilation.x[iteration.toInt()]

        for ((checkPoint, _) in limits) {
            bunches.asSequence()
                .filter { checkPoint(it) }
                .forEach {
                    val fleet = this.fleet[checkPoint] as LinearSymbol
                    fleet.flush()
                    (fleet.polynomial as LinearPolynomial) += xi[it]!!
                }
        }

        return Ok(success)
    }
}
