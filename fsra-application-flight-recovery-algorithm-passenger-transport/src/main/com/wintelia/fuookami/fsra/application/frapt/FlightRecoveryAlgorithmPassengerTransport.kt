package com.wintelia.fuookami.fsra.application.frapt

import kotlin.time.*
import kotlinx.datetime.*
import org.apache.logging.log4j.kotlin.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.FlightTaskContext
import com.wintelia.fuookami.fsra.domain.rule_context.RuleContext
import com.wintelia.fuookami.fsra.domain.rule_context.model.ShadowPriceMap
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.FlightRecoveryCompilationContext
import com.wintelia.fuookami.fsra.domain.bunch_generation_context.BunchGenerationContext
import com.wintelia.fuookami.fsra.domain.passenger_context.PassengerContext
import com.wintelia.fuookami.fsra.domain.cargo_context.CargoContext
import com.wintelia.fuookami.fsra.domain.crew_context.CrewContext
import fuookami.ospf.kotlin.core.backend.solver.config.LinearSolverConfig
import fuookami.ospf.kotlin.core.backend.solver.output.LinearSolverOutput

class FlightRecoveryAlgorithmPassengerTransport(
    private val heartBeatCallBack: ((String, Flt64) -> Unit)? = null,
    private val outputCallBack: ((String, Output) -> Unit)? = null,
    private val stoppedCallBack: (() -> Boolean)? = null
) {
    private val logger = logger()

    private val flightTaskContext: FlightTaskContext = FlightTaskContext()
    private val ruleContext: RuleContext = RuleContext(flightTaskContext)
    private val flightRecoveryCompilationContext: FlightRecoveryCompilationContext = FlightRecoveryCompilationContext(flightTaskContext, ruleContext)
    private val bunchGenerationContext: BunchGenerationContext = BunchGenerationContext(flightTaskContext, ruleContext)
    private val passengerContext: PassengerContext = PassengerContext()
    private val cargoContext: CargoContext = CargoContext()
    private val crewContext: CrewContext = CrewContext()

    private var fixedBunches = HashSet<FlightTaskBunch>()
    private var keptBunches = HashSet<FlightTaskBunch>()
    private var hiddenAircrafts = HashSet<Aircraft>()

    private var mainProblemSolvingTimes: UInt64 = UInt64.zero
    private var mainProblemSolvingTime: Duration = Duration.ZERO
    private var mainProblemModelingTime: Duration = Duration.ZERO
    private var subProblemSolvingTimes: UInt64 = UInt64.zero
    private var subProblemSolvingTime: Duration = Duration.ZERO

    private val columnAmount: UInt64 get() = flightRecoveryCompilationContext.columnAmount
    private val aircraftAmount: UInt64 get() = UInt64(flightRecoveryCompilationContext.recoveryNeededAircrafts.size.toULong())
    private fun notFixedAircraftAmount(fixedBunches: Set<FlightTaskBunch>): UInt64 = aircraftAmount - UInt64(fixedBunches.size.toULong())
    private fun minimumColumnAmount(fixedBunches: Set<FlightTaskBunch>, configuration: Configuration): UInt64 =
        notFixedAircraftAmount(fixedBunches) * configuration.minimumColumnAmountPerAircraft

    operator fun invoke(input: Input, configuration: Configuration, parameter: Parameter): Output {
        var maximumReducedCost1 = Flt64(50.0)
        var maximumReducedCost2 = Flt64(3000.0)

        val id = input.plan.id
        val beginTime = Clock.System.now()
        try {
            var bestOutput: Output? = null
            val model = LinearMetaModel(id)
            when (val ret = init(input, configuration, parameter)) {
                is Ok -> {}
                is Failed -> {
                    return Output(id, ret.error)
                }
            }

            lateinit var shadowPriceMap: ShadowPriceMap
            var iteration = Iteration()
            when (val ret = register(model, configuration)) {
                is Ok -> {}
                is Failed -> {
                    return tidyOutput(id, bestOutput, beginTime, ret.error)
                }
            }
            when (val ret = construct(input.plan, model, configuration, parameter)) {
                is Ok -> {}
                is Failed -> {
                    return tidyOutput(id, bestOutput, beginTime, ret.error)
                }
            }

            // solve ip with initial column
            val ipRet = when (val ret = solveMIP("${id}_$iteration", model, configuration)) {
                is Ok -> {
                    ret.value
                }

                is Failed -> {
                    return tidyOutput(id, bestOutput, beginTime, ret.error)
                }
            }
            bestOutput = when (val ret = analyzeSolution(input.plan, iteration.iteration, ipRet, model)) {
                is Ok -> {
                    ret.value
                }

                is Failed -> {
                    return tidyOutput(id, bestOutput, beginTime, ret.error)
                }
            }
            refresh(ipRet)
            iteration.refreshIpObj(ipRet.obj)
            if (eq(ipRet.obj, Flt64.zero)) {
                return tidyOutput(id, bestOutput, beginTime)
            }
            when (val ret = fixBunch(iteration.iteration, model)) {
                is Ok -> {}
                is Failed -> {
                    return tidyOutput(id, bestOutput, beginTime, ret.error)
                }
            }
            when (val ret = keepBunch(iteration.iteration, model)) {
                is Ok -> {}
                is Failed -> {
                    return tidyOutput(id, bestOutput, beginTime, ret.error)
                }
            }
            when (val ret = checkIsStopped(id, bestOutput, beginTime)) {
                is Output -> {
                    return ret
                }

                else -> {}
            }

            var mainIteration = UInt64.one
            while (!iteration.isImprovementSlow
                && iteration.runTime < configuration.timeLimit
            ) {
                logger.debug { "Iteration $mainIteration begin!" }

                shadowPriceMap = when (val ret = solveRMP(id, iteration, model, configuration, true)) {
                    is Ok -> {
                        ret.value
                    }

                    is Failed -> {
                        return tidyOutput(id, bestOutput, beginTime, ret.error)
                    }
                }
                when (val ret = hideAircrafts(model)) {
                    is Ok -> {}
                    is Failed -> {
                        return tidyOutput(id, bestOutput, beginTime, ret.error)
                    }
                }
                when (val ret = checkIsStopped(id, bestOutput, beginTime)) {
                    is Output -> {
                        return ret
                    }

                    else -> {}
                }

                logger.debug { "Global column generation of iteration $mainIteration begin!" }

                // globally column generation
                // it runs only 1 time
                for (count in 0 until 1) {
                    ++iteration
                    val newBunches = when (val ret = solveSP(id, flightRecoveryCompilationContext.recoveryNeededAircrafts, iteration, shadowPriceMap, configuration)) {
                        is Ok -> {
                            ret.value
                        }

                        is Failed -> {
                            return tidyOutput(id, bestOutput, beginTime, ret.error)
                        }
                    }
                    if (newBunches.isEmpty()) {
                        logger.debug { "There is no bunch generated in global column generation of iteration $mainIteration." }
                        if (eq(iteration.optimalRate, Flt64.one)) {
                            return tidyOutput(id, bestOutput, beginTime)
                        }
                    }
                    val newBunchAmount = UInt64(newBunches.size.toULong())

                    when (val ret = addColumns(iteration.iteration, newBunches, input.plan, model, configuration)) {
                        is Ok -> {}
                        is Failed -> {
                            return tidyOutput(id, bestOutput, beginTime, ret.error)
                        }
                    }
                    shadowPriceMap = when (val ret = solveRMP(id, iteration, model, configuration, true)) {
                        is Ok -> {
                            ret.value
                        }

                        is Failed -> {
                            return tidyOutput(id, bestOutput, beginTime, ret.error)
                        }
                    }
                    when (val ret = checkIsStopped(id, bestOutput, beginTime)) {
                        is Output -> {
                            return ret
                        }

                        else -> {}
                    }
                    val reducedAmount = UInt64(fixedBunches.count { gr(shadowPriceMap.reducedCost(it), Flt64.zero) }.toULong())
                    if (columnAmount > configuration.maximumColumnAmount) {
                        maximumReducedCost1 = when (val ret = removeColumns(maximumReducedCost1, shadowPriceMap, model, configuration)) {
                            is Ok -> {
                                ret.value
                            }

                            is Failed -> {
                                return tidyOutput(id, bestOutput, beginTime, ret.error)
                            }
                        }
                    }
                    if (reducedAmount >= configuration.badReducedAmount
                        || newBunchAmount <= minimumColumnAmount(fixedBunches, configuration)
                    ) {
                        break
                    }
                }
                maximumReducedCost1 = Flt64(50.0)

                logger.debug { "Global column generation of iteration $mainIteration end!" }

                val freeAircrafts = when (val ret = selectFreeAircrafts(shadowPriceMap, model, configuration)) {
                    is Ok -> {
                        ret.value
                    }

                    is Failed -> {
                        return tidyOutput(id, bestOutput, beginTime, ret.error)
                    }
                }
                val fixedBunches = when (val ret = globallyFix(freeAircrafts)) {
                    is Ok -> {
                        ret.value.toHashSet()
                    }

                    is Failed -> {
                        return tidyOutput(id, bestOutput, beginTime, ret.error)
                    }
                }
                val freeAircraftList = freeAircrafts.toMutableList()
                when (val ret = checkIsStopped(id, bestOutput, beginTime)) {
                    is Output -> {
                        return ret
                    }

                    else -> {}
                }

                logger.debug { "Local column generation of iteration $mainIteration begin!" }

                // locally column generation
                while (true) {
                    shadowPriceMap = when (val ret = solveRMP(id, iteration, model, configuration, false)) {
                        is Ok -> {
                            ret.value
                        }

                        is Failed -> {
                            return tidyOutput(id, bestOutput, beginTime, ret.error)
                        }
                    }
                    when (val ret = checkIsStopped(id, bestOutput, beginTime)) {
                        is Output -> {
                            return ret
                        }

                        else -> {}
                    }

                    ++iteration
                    val newBunches = when (val ret = solveSP(id, flightRecoveryCompilationContext.recoveryNeededAircrafts, iteration, shadowPriceMap, configuration)) {
                        is Ok -> {
                            ret.value
                        }

                        is Failed -> {
                            return tidyOutput(id, bestOutput, beginTime, ret.error)
                        }
                    }
                    if (newBunches.isEmpty()) {
                        --iteration
                        logger.debug { "There is no bunch generated in local column generation of iteration $mainIteration: $iteration." }
                        break
                    }
                    val newBunchAmount = UInt64(newBunches.size.toULong())

                    when (val ret = addColumns(iteration.iteration, newBunches, input.plan, model, configuration)) {
                        is Ok -> {}
                        is Failed -> {
                            return tidyOutput(id, bestOutput, beginTime, ret.error)
                        }
                    }
                    val newFixedBunches = when (val ret = locallyFix(iteration.iteration, fixedBunches, model)) {
                        is Ok -> {
                            ret.value
                        }

                        is Failed -> {
                            return tidyOutput(id, bestOutput, beginTime, ret.error)
                        }
                    }
                    if (newFixedBunches.isNotEmpty()) {
                        for (bunch in newFixedBunches) {
                            freeAircraftList.remove(bunch.aircraft)
                        }
                        fixedBunches.addAll(newFixedBunches)
                    } else {
                        break
                    }

                    if (columnAmount > configuration.maximumColumnAmount
                        && newBunchAmount > minimumColumnAmount(fixedBunches, configuration)
                    ) {
                        maximumReducedCost2 = when (val ret = removeColumns(maximumReducedCost2, shadowPriceMap, model, configuration)) {
                            is Ok -> {
                                ret.value
                            }

                            is Failed -> {
                                return tidyOutput(id, bestOutput, beginTime, ret.error)
                            }
                        }
                    }
                }

                logger.debug { "Local column generation of iteration $mainIteration end!" }

                this.fixedBunches = fixedBunches
                val lpRet = when (val ret = solveLP("${id}_${iteration}_lp", model, configuration, LinearSolverConfig())) {
                    is Ok -> {
                        ret.value
                    }

                    is Failed -> {
                        return tidyOutput(id, bestOutput, beginTime, ret.error)
                    }
                }
                refresh(lpRet)
                logIpResults(iteration.iteration, model)
                if (iteration.refreshIpObj(lpRet.obj)) {
                    when (val ret = analyzeSolution(input.plan, iteration.iteration, lpRet.result, model)) {
                        is Ok -> {
                            if (eq(lpRet.obj, Flt64.zero)) {
                                return tidyOutput(id, bestOutput, beginTime)
                            }
                        }

                        is Failed -> {
                            return tidyOutput(id, bestOutput, beginTime, ret.error)
                        }
                    }
                }
                heartBeat(id, iteration.optimalRate)
                when (val ret = checkIsStopped(id, bestOutput, beginTime)) {
                    is Output -> {
                        return ret
                    }

                    else -> {}
                }

                flush(iteration.iteration)
                iteration.halveStep()

                logger.debug { "Iteration $mainIteration end, optimal rate: ${String.format("%.2f", (iteration.optimalRate * Flt64(100.0)).toDouble())}%" }
                ++mainIteration
            }

            return tidyOutput(id, bestOutput, beginTime)
        } catch (e: Exception) {
            print(e.stackTraceToString())
            return Output(id, Err(ErrorCode.ApplicationException, e.message))
        }
    }

    private fun heartBeat(id: String, optimalRate: Flt64) {
        logger.info { "Heart beat, current optimal rate: ${String.format("%.2f", (optimalRate * Flt64(100.0)).toDouble())}%" }
        heartBeatCallBack?.let { it(id, optimalRate) }
    }

    private fun returnOutput(id: String, output: Output) {
        outputCallBack?.let { it(id, output) }
    }

    private fun stopped(): Boolean {
        return stoppedCallBack?.let { it() } ?: false
    }

    private fun init(input: Input, configuration: Configuration, parameter: Parameter): Try<Error> {
        when (val ret = flightTaskContext.init(input)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret = ruleContext.init(input, parameter)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret = flightRecoveryCompilationContext.init(input.plan, configuration)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        when (val ret =
            bunchGenerationContext.init(flightRecoveryCompilationContext.recoveryNeededAircrafts, flightRecoveryCompilationContext.recoveryNeededFlightTasks, configuration)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        if (configuration.withPassenger) {
            when (val ret = passengerContext.init(input)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCargo) {
            when (val ret = cargoContext.init(input)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCrew) {
            when (val ret = crewContext.init(input)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        return Ok(success)
    }

    private fun register(model: LinearMetaModel, configuration: Configuration): Try<Error> {
        val beginTime = Clock.System.now()
        when (val ret = flightRecoveryCompilationContext.register(model, configuration)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        if (configuration.withPassenger) {
            when (val ret = passengerContext.register(model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCargo) {
            when (val ret = cargoContext.register(model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCrew) {
            when (val ret = crewContext.register(model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        mainProblemModelingTime += Clock.System.now() - beginTime
        return Ok(success)
    }

    private fun construct(recoveryPlan: RecoveryPlan, model: LinearMetaModel, configuration: Configuration, parameter: Parameter): Try<Error> {
        val beginTime = Clock.System.now()
        when (val ret = flightRecoveryCompilationContext.construct(recoveryPlan, model, configuration, parameter)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        if (configuration.withPassenger) {
            when (val ret = passengerContext.construct(model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCargo) {
            when (val ret = cargoContext.construct(model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCrew) {
            when (val ret = crewContext.construct(model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        when (val ret = addColumns(UInt64.zero, bunchGenerationContext.initialFlightBunches, recoveryPlan, model, configuration)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        mainProblemModelingTime += Clock.System.now() - beginTime
        return Ok(success)
    }

    private fun solveRMP(id: String, iteration: Iteration, model: LinearMetaModel, configuration: Configuration, withKeeping: Boolean): Result<ShadowPriceMap, Error> {
        val lpRet = when (val ret = solveLP("${id}_${iteration}_lp", model, configuration, LinearSolverConfig())) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        refresh(lpRet)
        if (iteration.refreshLpObj(lpRet.result.obj) && withKeeping) {
            when (val ret = keepBunch(iteration.iteration, model)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        val shadowPriceMap = when (val ret = extractShadowPrice(model, lpRet.dualResult, configuration)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        return Ok(shadowPriceMap)
    }

    private fun solveSP(
        id: String,
        aircrafts: List<Aircraft>,
        iteration: Iteration,
        shadowPriceMap: ShadowPriceMap,
        configuration: Configuration
    ): Result<List<FlightTaskBunch>, Error> {
        val beginTime = Clock.System.now()
        val newBunches = when (val ret = bunchGenerationContext.generateFlightTaskBunch(aircrafts, iteration.iteration, shadowPriceMap, configuration)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        subProblemSolvingTimes += UInt64.one
        subProblemSolvingTime = Clock.System.now() - beginTime
        iteration.refreshLowerBound(shadowPriceMap, newBunches)
        heartBeat(id, iteration.optimalRate)
        return Ok(newBunches)
    }

    private fun extractShadowPrice(model: LinearMetaModel, shadowPrices: List<Flt64>, configuration: Configuration): Result<ShadowPriceMap, Error> {
        val map = ShadowPriceMap()

        when (val ret = flightRecoveryCompilationContext.extractShadowPrice(map, model, shadowPrices)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        if (configuration.withPassenger) {
            when (val ret = passengerContext.extractShadowPrice(map, model, shadowPrices)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCargo) {
            when (val ret = cargoContext.extractShadowPrice(map, model, shadowPrices)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCrew) {
            when (val ret = crewContext.extractShadowPrice(map, model, shadowPrices)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        return Ok(map)
    }

    private fun globallyFix(freeAircrafts: Set<Aircraft>): Result<Set<FlightTaskBunch>, Error> {
        val fixedBunches = HashSet<FlightTaskBunch>()
        for (bunch in this.fixedBunches) {
            if (!freeAircrafts.contains(bunch.aircraft)) {
                fixedBunches.add(bunch)
            }
        }

        when (val ret = flightRecoveryCompilationContext.globallyFix(fixedBunches)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        return Ok(fixedBunches)
    }

    private fun locallyFix(iteration: UInt64, fixedBunches: Set<FlightTaskBunch>, model: LinearMetaModel): Result<Set<FlightTaskBunch>, Error> {
        val fixBar = Flt64(0.9)
        return when (val ret = flightRecoveryCompilationContext.locallyFix(iteration, fixBar, fixedBunches, model)) {
            is Ok -> {
                Ok(ret.value)
            }

            is Failed -> {
                Failed(ret.error)
            }
        }
    }

    private fun flush(iteration: UInt64): Try<Error> {
        when (val ret = flightRecoveryCompilationContext.flush(iteration)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        keptBunches.clear()
        hiddenAircrafts.clear()
        return Ok(success)
    }

    private fun analyzeSolution(recoveryPlan: RecoveryPlan, iteration: UInt64, solverOutput: LinearSolverOutput, model: LinearMetaModel): Result<Output, Error> {
        val (flights, maintenances) = when (val ret = flightRecoveryCompilationContext.analyzeSolution(recoveryPlan, iteration, model)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        return Ok(
            Output(
                recoveryPlanId = recoveryPlan.id,
                success = true,
                errorCode = null,
                errorMessage = null,
                obj = solverOutput.obj,
                flights = flights,
                maintenances = maintenances
            )
        )
    }

    private fun addColumns(iteration: UInt64, bunches: List<FlightTaskBunch>, recoveryPlan: RecoveryPlan, model: LinearMetaModel, configuration: Configuration): Try<Error> {
        val beginTime = Clock.System.now()
        when (val ret = flightRecoveryCompilationContext.addColumns(iteration, bunches, recoveryPlan, model, configuration)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        if (configuration.withPassenger) {
            when (val ret = passengerContext.addColumns(iteration, bunches)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCargo) {
            when (val ret = cargoContext.addColumns(iteration, bunches)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        if (configuration.withCrew) {
            when (val ret = crewContext.addColumns(iteration, bunches)) {
                is Ok -> {}
                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        mainProblemModelingTime += Clock.System.now() - beginTime
        return Ok(success)
    }

    private fun removeColumns(maximumReducedCost: Flt64, shadowPriceMap: ShadowPriceMap, model: LinearMetaModel, configuration: Configuration): Result<Flt64, Error> {
        return flightRecoveryCompilationContext.removeColumns(maximumReducedCost, configuration.maximumColumnAmount, shadowPriceMap, fixedBunches, keptBunches, model)
    }

    private fun fixBunch(iteration: UInt64, model: LinearMetaModel): Try<Error> {
        return when (val ret = flightRecoveryCompilationContext.extractFixedBunches(iteration, model)) {
            is Ok -> {
                fixedBunches.addAll(ret.value)
                Ok(success)
            }

            is Failed -> {
                Failed(ret.error)
            }
        }
    }

    private fun keepBunch(iteration: UInt64, model: LinearMetaModel): Try<Error> {
        return when (val ret = flightRecoveryCompilationContext.extractKeptFlightBunches(iteration, model)) {
            is Ok -> {
                keptBunches.addAll(ret.value)
                Ok(success)
            }

            is Failed -> {
                Failed(ret.error)
            }
        }
    }

    private fun hideAircrafts(model: LinearMetaModel): Try<Error> {
        return when (val ret = flightRecoveryCompilationContext.extractHiddenAircrafts(model)) {
            is Ok -> {
                hiddenAircrafts.addAll(ret.value)
                Ok(success)
            }

            is Failed -> {
                Failed(ret.error)
            }
        }
    }

    private fun selectFreeAircrafts(shadowPriceMap: ShadowPriceMap, model: LinearMetaModel, configuration: Configuration): Result<Set<Aircraft>, Error> {
        return flightRecoveryCompilationContext.selectFreeAircrafts(fixedBunches, hiddenAircrafts, shadowPriceMap, model, configuration)
    }

    private fun refresh(lpResult: LPResult) {
        mainProblemSolvingTimes += UInt64.one
        mainProblemSolvingTime += lpResult.result.time
    }

    private fun refresh(ipResult: IPResult) {
        mainProblemSolvingTimes += UInt64.one
        mainProblemSolvingTime += ipResult.time
    }

    private fun checkIsStopped(id: String, bestOutput: Output?, beginTime: Instant): Output? {
        return if (stopped()) {
            logger.info { "Stopped!" }
            if (bestOutput != null) {
                tidyOutput(id, bestOutput, beginTime)
            } else {
                Output(id, Err(ErrorCode.ApplicationStop))
            }
        } else {
            null
        }
    }

    private fun tidyOutput(id: String, bestOutput: Output?, beginTime: Instant, error: Error): Output {
        return if (bestOutput != null) {
            tidyOutput(id, bestOutput, beginTime)
        } else {
            logger.info { "Stopped: ${error.code()}, ${error.message()}." }
            Output(id, error)
        }
    }

    private fun tidyOutput(id: String, bestOutput: Output, beginTime: Instant): Output {
        bestOutput.performance = Performance(
            mainProblemSolvingTimes = mainProblemSolvingTimes,
            mainProblemSolvingTime = UInt64(mainProblemSolvingTime.toLong(DurationUnit.MILLISECONDS).toULong()),
            mainProblemModelingTime = UInt64(mainProblemModelingTime.toLong(DurationUnit.MILLISECONDS).toULong()),
            subProblemSolvingTimes = subProblemSolvingTimes,
            subProblemSolvingTime = UInt64(subProblemSolvingTime.toLong(DurationUnit.MILLISECONDS).toULong()),
            useTime = UInt64((Clock.System.now() - beginTime).toLong(DurationUnit.MILLISECONDS).toULong())
        )
        returnOutput(id, bestOutput)
        return bestOutput
    }

    private fun logLpResults(iteration: UInt64, model: LinearMetaModel): Try<Error> {
        flightRecoveryCompilationContext.logResult(iteration, model)
        return Ok(success)
    }

    private fun logIpResults(iteration: UInt64, model: LinearMetaModel): Try<Error> {
        when (val ret = logLpResults(iteration, model)) {
            is Ok -> {}
            is Failed -> {
                return Failed(ret.error)
            }
        }
        flightRecoveryCompilationContext.logBunchCost(iteration, model)
        return Ok(success)
    }
}
