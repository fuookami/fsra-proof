package com.wintelia.fuookami.fsra.infrastructure

import gurobi.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.core.backend.intermediate_model.*
import fuookami.ospf.kotlin.core.backend.solver.config.*
import fuookami.ospf.kotlin.core.backend.solver.output.*
import fuookami.ospf.kotlin.core.backend.plugins.gurobi.*
import fuookami.ospf.kotlin.core.backend.plugins.cplex.*
import fuookami.ospf.kotlin.core.backend.plugins.scip.*

typealias IPResult = LinearSolverOutput

fun solveMIP(name: String, metaModel: LinearMetaModel, configuration: Configuration, solverConfig: LinearSolverConfig = LinearSolverConfig()): Result<IPResult, Error> {
    // metaModel.export("$name.opm")
    val model = LinearTriadModel(LinearModel(metaModel))
    // model.export("$name.lp", ModelFileFormat.LP)
    val ret = when (configuration.solver) {
        "cplex" -> {
            val solver = CplexLinearSolver(solverConfig)
            solver(model)
        }

        "gurobi" -> {
            val solver = GurobiLinearSolver(solverConfig)
            solver(model)
        }

        "scip" -> {
            val solver = SCIPLinearSolver(solverConfig)
            solver(model)
        }

        else -> {
            return Failed(Err(ErrorCode.SolverNotFound, configuration.solver))
        }
    }
    return when (ret) {
        is Ok -> {
            metaModel.tokens.setSolution(ret.value.results)
            Ok(ret.value)
        }

        is Failed -> {
            Failed(ret.error)
        }
    }
}

data class LPResult(
    val obj: Flt64,
    val result: LinearSolverOutput,
    val dualResult: List<Flt64>
)

fun solveLP(name: String, metaModel: LinearMetaModel, configuration: Configuration, solverConfig: LinearSolverConfig = LinearSolverConfig()): Result<LPResult, Error> {
    metaModel.export("$name.opm")
    lateinit var dualResult: List<Flt64>
    val model = LinearTriadModel(LinearModel(metaModel))
    model.linearRelax()
    model.export("${name}_lp.lp", ModelFileFormat.LP)
    val ret = when (configuration.solver) {
        "cplex" -> {
            val callBack = CplexSolverCallBack().analyzingSolution { cplex, _, constraints ->
                dualResult = constraints.map { Flt64(cplex.getDual(it)) }
            }
            val solver = CplexLinearSolver(solverConfig, callBack)
            solver(model)
        }

        "gurobi" -> {
            val callBack = GurobiSolverCallBack().analyzingSolution { _, _, constraints ->
                dualResult = constraints.map { Flt64(it.get(GRB.DoubleAttr.Pi)) }
            }
            val solver = GurobiLinearSolver(solverConfig, callBack)
            solver(model)
        }

        "scip" -> {
            lateinit var dualModel: LinearTriadModel

            val solver = SCIPLinearSolver(solverConfig)
            val dualModelGenerator = Thread {
                val temp = model.copy()
                temp.normalize()
                dualModel = temp.dual()
            }
            dualModelGenerator.start()

            val ret = solver(model)
            if (ret is Failed) {
                return Failed(ret.error)
            }
            dualModelGenerator.join()

            when (val dualRet = solver(dualModel)) {
                is Ok -> {
                    dualResult = dualRet.value.results
                }

                is Failed -> {
                    return Failed(dualRet.error)
                }
            }
            ret
        }

        else -> {
            return Failed(Err(ErrorCode.SolverNotFound, configuration.solver))
        }
    }

    return when (ret) {
        is Ok -> {
            metaModel.tokens.setSolution(ret.value()!!.results)
            Ok(LPResult(ret.value()!!.obj, ret.value()!!, dualResult))
        }

        is Failed -> {
            Failed(ret.error)
        }
    }
}
