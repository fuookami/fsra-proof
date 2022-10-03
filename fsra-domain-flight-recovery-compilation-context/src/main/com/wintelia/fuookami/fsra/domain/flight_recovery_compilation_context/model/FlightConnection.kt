package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.LinearPolynomial

class FlightConnection {
    lateinit var connection: LinearSymbols1
    lateinit var k: UIntVariable1

    fun register(connectingFlightPairs: List<ConnectingFlightPair>, model: LinearMetaModel): Try<Error> {
        if (connectingFlightPairs.isNotEmpty()) {
            if (!this::connection.isInitialized) {
                connection = LinearSymbols1("connection", Shape1(connectingFlightPairs.size))
                for (pair in connectingFlightPairs) {
                    connection[pair] = LinearSymbol(LinearPolynomial(), "${connection.name}_${pair}")
                }
            }
            model.addSymbols(connection)

            if (!this::connection.isInitialized) {
                k = UIntVariable1("k", Shape1(connectingFlightPairs.size))
                for (pair in connectingFlightPairs) {
                    k[pair]!!.name = "${k.name}_${pair}"
                }
            }
            model.addVars(k)
        }

        return Ok(success)
    }

    fun addColumns(
        iteration: UInt64,
        bunches: List<FlightTaskBunch>,
        connectingFlightPairs: List<ConnectingFlightPair>,
        compilation: Compilation
    ): Try<Error> {
        assert(bunches.isNotEmpty())

        val xi = compilation.x[iteration.toInt()]

        for (pair in connectingFlightPairs) {
            bunches.asSequence()
                .filter { it.contains(Pair(pair.prevTask, pair.nextTask)) }
                .forEach {
                    val connection = this.connection[pair]!! as LinearSymbol
                    connection.flush()
                    (connection.polynomial as LinearPolynomial) += xi[it]!!
                }
        }

        return Ok(success)
    }
}
