package com.wintelia.fuookami.fsra.domain.passenger_context.service

import java.time.format.*
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.PassengerClass
import com.wintelia.fuookami.fsra.domain.passenger_context.*
import com.wintelia.fuookami.fsra.domain.passenger_context.model.*

class OutputAnalyzer(
    private val aggregation: Aggregation
) {
    data class Output(
        val recoveryedFlights: List<RecoveryedFlightDTO>
    )

    operator fun invoke(iteration: UInt64, model: LinearMetaModel): Result<Solution, Error> {
        val actualAmount = HashMap<FlightTask, Map<PassengerClass, UInt64>>()
        val canceledAmount = HashMap<FlightTask, Map<PassengerClass, UInt64>>()
        val classChangeAmount = HashMap<FlightTask, Map<Pair<PassengerClass, PassengerClass>, UInt64>>()

        for ((i, flight) in aggregation.flights.withIndex()) {
            val thisActualAmount = HashMap<PassengerClass, UInt64>()
            for (cls in PassengerClass.values()) {
                val symbol = aggregation.passengerActualAmount.pa[i, cls.ordinal]!! as LinearSymbol
                val value = symbol.polynomial.value(model.tokens.tokenList)
                if (value geq Flt64.one) {
                    thisActualAmount[cls] = value.round().toUInt64()
                }
            }
            actualAmount[flight] = thisActualAmount

            val thisCanceledAmount = HashMap<PassengerClass, UInt64>()
            for (pf in aggregation.passengerGroup[flight]!!) {
                val token = model.tokens.token(aggregation.passengerCancel.pc[pf]!!) ?: continue
                val value = token.result ?: Flt64.zero
                if (value geq Flt64.one) {
                    thisCanceledAmount[pf.cls] = (thisCanceledAmount[pf.cls] ?: UInt64.zero) + value.round().toUInt64()
                }
            }
            canceledAmount[flight] = thisCanceledAmount

            val thisClassChangeAmount = HashMap<Pair<PassengerClass, PassengerClass>, UInt64>()
            for (pf in aggregation.passengerGroup[flight]!!) {
                for (cls in PassengerClass.values()) {
                    if (pf.cls != cls) {
                        val token = model.tokens.token(aggregation.passengerClassChange.pcc[pf, cls]!!) ?: continue
                        val value = token.result ?: Flt64.zero
                        if (value geq Flt64.one) {
                            thisClassChangeAmount[Pair(pf.cls, cls)] = (thisClassChangeAmount[Pair(pf.cls, cls)] ?: UInt64.zero) + value.round().toUInt64()
                        }
                    }
                }
            }
            classChangeAmount[flight] = thisClassChangeAmount
        }

        return Ok(Solution(actualAmount, canceledAmount, classChangeAmount))
    }

    operator fun invoke(recoveryPlan: RecoveryPlan, recoveryedFlights: List<Pair<RecoveryedFlightDTO, FlightTask?>>, iteration: UInt64, model: LinearMetaModel): Result<Output, Error> {
        val solution = when (val ret = this(iteration, model)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }

        val visitedFlightTask = HashSet<FlightTask>()
        val recoveryedFlightsWithPassenger = ArrayList<RecoveryedFlightDTO>()
        for ((dto, flight) in recoveryedFlights) {
            when (val ret = if (flight == null) {
                val originFlight = aggregation.flights.find { dto.id == it.actualId } ?: continue
                visitedFlightTask.add(originFlight)
                analyzeCanceledFlight(dto, originFlight, aggregation.passengerGroup[originFlight]!!)
            } else {
                visitedFlightTask.add(flight.originTask)
                analyzeRecoveryedFlight(dto, flight, aggregation.passengerGroup[flight]!!, solution.actualAmount[flight] ?: emptyMap(), solution.canceledAmount[flight] ?: emptyMap(), solution.classChangeAmount[flight] ?: emptyMap())
            }) {
                is Ok -> { }
                is Failed -> { return Failed(ret.error) }
            }
            recoveryedFlightsWithPassenger.add(dto)
        }

        for (flight in aggregation.flights) {
            if (visitedFlightTask.contains(flight)
                || (!solution.canceledAmount.containsKey(flight.originTask)
                        && !solution.classChangeAmount.containsKey(flight.originTask)
                )
            ) {
                continue
            }

            when (val ret = analyzeNotRecoveryedFlight(recoveryPlan, flight, aggregation.passengerGroup[flight]!!, solution.actualAmount[flight] ?: emptyMap(), solution.canceledAmount[flight] ?: emptyMap(), solution.classChangeAmount[flight] ?: emptyMap())) {
                is Ok -> { recoveryedFlightsWithPassenger.add(ret.value) }
                is Failed -> { return Failed(ret.error) }
            }
        }

        return Ok(Output(recoveryedFlightsWithPassenger))
    }

    private fun analyzeRecoveryedFlight(dto: RecoveryedFlightDTO, flight: FlightTask, passengerFlights: List<PassengerFlight>, actualAmount: Map<PassengerClass, UInt64>, canceledAmount: Map<PassengerClass, UInt64>, classChangeAmount: Map<Pair<PassengerClass, PassengerClass>, UInt64>): Try<Error> {
        val originAmount = passengerFlights.groupBy { it.cls }.map { Pair(it.key, UInt64(it.value.sumOf { pf -> pf.amount.toInt() }.toULong())) }.toMap()

        dto.oldFirstClassCapacity = flight.plan.aircraft?.capacity(PassengerClass.First) ?: UInt64.zero
        dto.firstClassCapacity = flight.aircraft?.capacity(PassengerClass.First) ?: UInt64.zero
        dto.oldFirstClassAmount = originAmount[PassengerClass.First] ?: UInt64.zero
        dto.firstClassAmount = actualAmount[PassengerClass.First] ?: UInt64.zero
        dto.firstClassCanceledAmount = canceledAmount[PassengerClass.First] ?: UInt64.zero
        dto.firstClassFromBusinessClassAmount = classChangeAmount[Pair(PassengerClass.Business, PassengerClass.First)] ?: UInt64.zero
        dto.firstClassFromEconomyClassAmount = classChangeAmount[Pair(PassengerClass.Economy, PassengerClass.First)] ?: UInt64.zero

        dto.oldBusinessClassCapacity = flight.plan.aircraft?.capacity(PassengerClass.Business) ?: UInt64.zero
        dto.businessClassCapacity = flight.aircraft?.capacity(PassengerClass.Business) ?: UInt64.zero
        dto.oldBusinessClassAmount = originAmount[PassengerClass.Business] ?: UInt64.zero
        dto.businessClassAmount = actualAmount[PassengerClass.Business] ?: UInt64.zero
        dto.businessClassCanceledAmount = canceledAmount[PassengerClass.Business] ?: UInt64.zero
        dto.businessClassFromFirstClassAmount = classChangeAmount[Pair(PassengerClass.First, PassengerClass.Business)] ?: UInt64.zero
        dto.businessClassFromEconomyClassAmount = classChangeAmount[Pair(PassengerClass.Economy, PassengerClass.Business)] ?: UInt64.zero

        dto.oldEconomyClassCapacity = flight.plan.aircraft?.capacity(PassengerClass.Economy) ?: UInt64.zero
        dto.economyClassCapacity = flight.aircraft?.capacity(PassengerClass.Economy) ?: UInt64.zero
        dto.oldEconomyClassAmount = originAmount[PassengerClass.Economy] ?: UInt64.zero
        dto.economyClassAmount = actualAmount[PassengerClass.Economy] ?: UInt64.zero
        dto.economyClassCanceledAmount = canceledAmount[PassengerClass.Economy] ?: UInt64.zero
        dto.economyClassFromFirstClassAmount = classChangeAmount[Pair(PassengerClass.First, PassengerClass.Economy)] ?: UInt64.zero
        dto.economyClassFromBusinessClassAmount = classChangeAmount[Pair(PassengerClass.Business, PassengerClass.Economy)] ?: UInt64.zero

        return Ok(success)
    }

    private fun analyzeNotRecoveryedFlight(recoveryPlan: RecoveryPlan, flight: FlightTask, passengerFlights: List<PassengerFlight>, actualAmount: Map<PassengerClass, UInt64>, canceledAmount: Map<PassengerClass, UInt64>, classChangeAmount: Map<Pair<PassengerClass, PassengerClass>, UInt64>): Result<RecoveryedFlightDTO, Error> {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(TimeZone.currentSystemDefault().toJavaZoneId())

        val dto = RecoveryedFlightDTO(
            recoveryPlanId = recoveryPlan.id,
            id = flight.actualId,
            no = if (flight.type is FlightFlightTask) {
                (flight as Flight).plan.no
            } else {
                ""
            },
            dep = flight.dep.icao,
            arr = flight.arr.icao,
            oldAcType = if (flight.originTask.aircraft != null) {
                flight.originTask.aircraft!!.minorType.code
            } else {
                null
            },
            newAcType = flight.aircraft!!.minorType.code,
            oldAcReg = if (flight.originTask.aircraft != null) {
                flight.originTask.aircraft!!.regNo
            } else {
                null
            },
            newAcReg = flight.aircraft!!.regNo,
            date = flight.time?.let { Date(it.begin) }.toString(),
            std = flight.scheduledTime?.begin?.toJavaInstant()?.let { dateTimeFormatter.format(it) },
            sta = flight.scheduledTime?.end?.toJavaInstant()?.let { dateTimeFormatter.format(it) },
            etd = flight.time?.begin?.toJavaInstant()?.let { dateTimeFormatter.format(it) },
            eta = flight.time?.end?.toJavaInstant()?.let { dateTimeFormatter.format(it) },
        )
        return when (val ret = analyzeRecoveryedFlight(dto, flight, passengerFlights, actualAmount, canceledAmount, classChangeAmount)) {
            is Ok -> { Ok(dto) }
            is Failed -> { Failed(ret.error) }
        }
    }

    private fun analyzeCanceledFlight(dto: RecoveryedFlightDTO, flight: FlightTask, passengerFlights: List<PassengerFlight>): Try<Error> {
        val originAmount = passengerFlights.groupBy { it.cls }.map { Pair(it.key, UInt64(it.value.sumOf { pf -> pf.amount.toInt() }.toULong())) }.toMap()

        dto.oldFirstClassCapacity = flight.plan.aircraft?.capacity(PassengerClass.First) ?: UInt64.zero
        dto.oldFirstClassAmount = originAmount[PassengerClass.First] ?: UInt64.zero
        dto.firstClassCanceledAmount = originAmount[PassengerClass.First] ?: UInt64.zero

        dto.oldBusinessClassCapacity = flight.plan.aircraft?.capacity(PassengerClass.Business) ?: UInt64.zero
        dto.oldBusinessClassAmount = originAmount[PassengerClass.Business] ?: UInt64.zero
        dto.businessClassCanceledAmount = originAmount[PassengerClass.Business] ?: UInt64.zero

        dto.oldEconomyClassCapacity = flight.plan.aircraft?.capacity(PassengerClass.Economy) ?: UInt64.zero
        dto.oldEconomyClassAmount = originAmount[PassengerClass.Economy] ?: UInt64.zero
        dto.economyClassCanceledAmount = originAmount[PassengerClass.Economy] ?: UInt64.zero

        return Ok(success)
    }
}
