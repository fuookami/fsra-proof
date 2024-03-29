package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.FlightLink
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.LinearPolynomial
import fuookami.ospf.kotlin.utils.concept.ManualIndexed
import fuookami.ospf.kotlin.utils.parallel.ThreadGuard

class FlightLink(
    linkPairs: List<FlightLink>
) {
    val linkPairs: List<FlightLink> = linkPairs.filter { it.prevTask.indexed && it.succTask.indexed }
    lateinit var link: LinearSymbols1
    lateinit var k: BinVariable1

    init {
        ManualIndexed.flush<FlightLink>()
        for (pair in this.linkPairs) {
            pair.setIndexed(FlightLink::class)
        }
    }

    fun register(model: LinearMetaModel): Try<Error> {
        if (linkPairs.isNotEmpty()) {
            if (!this::link.isInitialized) {
                link = LinearSymbols1("link", Shape1(linkPairs.size))
                for (pair in linkPairs) {
                    link[pair] = LinearSymbol(LinearPolynomial(), "${link.name}_${pair}_${pair.index}")
                }
            }
            model.addSymbols(link)

            if (!this::k.isInitialized) {
                k = BinVariable1("k", Shape1(linkPairs.size))
                for (pair in linkPairs) {
                    k[pair]!!.name = "${k.name}_${pair}_${pair.index}"
                }
            }
            model.addVars(k)
        }

        return Ok(success)
    }

    fun addColumns(
        iteration: UInt64,
        bunches: List<FlightTaskBunch>,
        compilation: Compilation
    ): Try<Error> {
        assert(bunches.isNotEmpty())

        val xi = compilation.x[iteration.toInt()]

        for (pair in linkPairs) {
            bunches.asSequence()
                .filter { it.contains(Pair(pair.prevTask, pair.succTask)) }
                .forEach {
                    val link = this.link[pair]!! as LinearSymbol
                    link.flush()
                    (link.polynomial as LinearPolynomial) += xi[it]!!
                }
        }
        for (pair in linkPairs) {
            (this.link[pair]!! as LinearSymbol).cells
        }

        return Ok(success)
    }
}
