package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.model

import kotlin.math.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.concept.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class Flow(
    flowControls: List<FlowControl>
) {
    data class CheckPoint(
        val flowControl: FlowControl,
        val time: TimeRange,
        val indexInRule: UInt64,
    ): AutoIndexed(CheckPoint::class) {
        val airport by flowControl::airport
        val scene by flowControl::scene
        val condition by flowControl::condition
        val capacity get() = flowControl.capacity.amount
        val interval get() = flowControl.capacity.interval

        operator fun invoke(prevTask: FlightTask?, task: FlightTask?): Boolean {
            return scene(prevTask, task, airport, time, condition)
        }

        operator fun invoke(bunch: FlightTaskBunch): UInt64 {
            return scene(bunch, airport, time, condition)
        }

        override fun toString() = "${flowControl.name}_${((time.begin - flowControl.time.begin) / interval).roundToInt()}"
    }

    val checkPoints: List<CheckPoint>
    lateinit var flow: LinearSymbols1
    lateinit var m: UIntVariable1

    init {
        val checkPoints = ArrayList<CheckPoint>()
        for (rule in flowControls) {
            var index = UInt64.zero
            var beginTime = rule.time.begin
            while (beginTime <= rule.time.end) {
                val time = TimeRange(beginTime, beginTime + minOf(rule.time.end - beginTime, rule.capacity.interval))
                checkPoints.add(CheckPoint(rule, time, index))
                beginTime += rule.capacity.interval
            }
            ++index
        }
        this.checkPoints = checkPoints
    }

    fun register(model: LinearMetaModel): Try<Error> {
        if (checkPoints.isNotEmpty()) {
            if (!this::flow.isInitialized) {
                flow = LinearSymbols1("flow", Shape1(checkPoints.size))
                for (checkPoint in checkPoints) {
                    flow[checkPoint] = LinearSymbol(LinearPolynomial(), "${flow.name}_${checkPoint}")
                }
            }
            model.addSymbols(flow)

            if (!this::m.isInitialized) {
                m = UIntVariable1("m", Shape1(checkPoints.size))
                for (checkPoint in checkPoints) {
                    m[checkPoint]!!.name = "${m.name}_${checkPoint}"
                }
            }
            model.addVars(m)
        }

        return Ok(success)
    }

    fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>, compilation: Compilation): Try<Error> {
        assert(bunches.isNotEmpty())

        val xi = compilation.x[iteration.toInt()]

        for (checkPoint in checkPoints) {
            for (bunch in bunches) {
                val amount = checkPoint(bunch)
                if (amount != UInt64.zero) {
                    val flow = this.flow[checkPoint]!! as LinearSymbol
                    flow.flush()
                    (flow.polynomial as LinearPolynomial) += amount * xi[bunch]!!
                }
            }
        }

        return Ok(success)
    }
}
