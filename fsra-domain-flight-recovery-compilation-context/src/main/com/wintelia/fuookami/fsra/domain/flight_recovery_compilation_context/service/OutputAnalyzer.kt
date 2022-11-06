package com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.service

import java.time.format.*
import kotlin.time.*
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.domain.flight_recovery_compilation_context.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class OutputAnalyzer(
    private val aggregation: Aggregation
) {
    data class Output(
        val recoveryedFlights: List<Pair<RecoveryedFlightDTO, FlightTask?>>,
        val recoveryedMaintenances: List<RecoveryedMaintenanceDTO>
    )

    operator fun invoke(iteration: UInt64, model: LinearMetaModel): Result<Solution, Error> {
        val selectedBunches = when (val ret = aggregation.extractFixedBunches(iteration, model)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }

        val recoveryedFlights = ArrayList<FlightTask>()
        val recoveryedMaintenances = ArrayList<FlightTask>()
        val canceledFlights = ArrayList<FlightTask>()
        val canceledMaintenances = ArrayList<FlightTask>()
        for (flightTask in aggregation.recoveryNeededFlightTasks) {
            var flag = false
            for (bunch in selectedBunches) {
                val recoveryedFlightTask = bunch.get(flightTask)
                if (recoveryedFlightTask != null) {
                    if (recoveryedFlightTask.recovered) {
                        if (recoveryedFlightTask.isFlight) {
                            val policy = recoveryedFlightTask.recoveryPolicy
                            if (policy.aircraft != null || policy.route != null) {
                                recoveryedFlights.add(recoveryedFlightTask)
                            } else if (policy.time != null && policy.time != recoveryedFlightTask.scheduledTime && policy.time != recoveryedFlightTask.plan.time) {
                                recoveryedFlights.add(recoveryedFlightTask)
                            }
                        } else {
                            recoveryedMaintenances.add(recoveryedFlightTask)
                        }
                    }
                    flag = true
                    break
                }
            }

            if (!flag) {
                if (flightTask.isFlight) {
                    canceledFlights.add(flightTask)
                } else {
                    canceledMaintenances.add(flightTask)
                }
            }
        }
        return Ok(
            Solution(
                recoveryedFlights = recoveryedFlights,
                canceledFlights = canceledFlights,
                recoveryedMaintenances = recoveryedMaintenances,
                canceledMaintenances = canceledMaintenances,
            )
        )
    }

    operator fun invoke(recoveryPlan: RecoveryPlan, iteration: UInt64, model: LinearMetaModel): Result<Output, Error> {
        val solution = when (val ret = this(iteration, model)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        val recoveryedFlights = when (val ret = analyzeFlightSolution(recoveryPlan, solution.recoveryedFlights, solution.canceledFlights)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        val recoveryedMaintenances = when (val ret = analyzeMaintenanceSolution(recoveryPlan, solution.recoveryedMaintenances, solution.canceledMaintenances)) {
            is Ok -> {
                ret.value
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        return Ok(
            Output(
                recoveryedFlights = recoveryedFlights,
                recoveryedMaintenances = recoveryedMaintenances
            )
        )
    }

    private fun analyzeFlightSolution(
        recoveryPlan: RecoveryPlan,
        recoveryedFlights: List<FlightTask>,
        canceledFlights: List<FlightTask>
    ): Result<List<Pair<RecoveryedFlightDTO, FlightTask?>>, Error> {
        val flights = ArrayList<Pair<RecoveryedFlightDTO, FlightTask?>>()
        for (recoveryedFlight in recoveryedFlights) {
            when (val ret = dumpFlight(recoveryPlan, recoveryedFlight, false)) {
                is Ok -> {
                    flights.add(Pair(ret.value, recoveryedFlight))
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        for (canceledFlight in canceledFlights) {
            if (canceledFlight.type is TransferFlightFlightTask) {
                continue
            }
            // todo: if implement straight flight

            when (val ret = dumpFlight(recoveryPlan, canceledFlight, true)) {
                is Ok -> {
                    flights.add(Pair(ret.value, null))
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        return Ok(flights)
    }

    private fun analyzeMaintenanceSolution(
        recoveryPlan: RecoveryPlan,
        recoveryedMaintenances: List<FlightTask>,
        canceledMaintenances: List<FlightTask>
    ): Result<List<RecoveryedMaintenanceDTO>, Error> {
        val maintenances = ArrayList<RecoveryedMaintenanceDTO>()
        for (recoveryedMaintenance in recoveryedMaintenances) {
            when (val ret = dumpMaintenance(recoveryPlan, recoveryedMaintenance, false)) {
                is Ok -> {
                    maintenances.add(ret.value)
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        for (canceledMaintenance in canceledMaintenances) {
            when (val ret = dumpMaintenance(recoveryPlan, canceledMaintenance, true)) {
                is Ok -> {
                    maintenances.add(ret.value)
                }

                is Failed -> {
                    return Failed(ret.error)
                }
            }
        }
        return Ok(maintenances)
    }

    private fun dumpFlight(recoveryPlan: RecoveryPlan, flight: FlightTask, canceled: Boolean): Result<RecoveryedFlightDTO, Error> {
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(TimeZone.currentSystemDefault().toJavaZoneId())

        return Ok(
            RecoveryedFlightDTO(
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
                newAcType = if (canceled) {
                    null
                } else {
                    flight.aircraft!!.minorType.code
                },
                oldAcReg = if (flight.originTask.aircraft != null) {
                    flight.originTask.aircraft!!.regNo
                } else {
                    null
                },
                newAcReg = if (canceled) {
                    null
                } else {
                    flight.aircraft!!.regNo
                },
                date = flight.time?.let { Date(it.begin) }.toString(),
                std = flight.scheduledTime?.begin?.toJavaInstant()?.let { dateTimeFormatter.format(it) },
                sta = flight.scheduledTime?.end?.toJavaInstant()?.let { dateTimeFormatter.format(it) },
                etd = if (canceled) {
                    when (flight) {
                        is Flight -> { flight.plan.estimatedTime?.begin?.toJavaInstant()?.let { dateTimeFormatter.format(it) } }
                        else -> { null }
                    }
                } else {
                    flight.time?.begin?.toJavaInstant()?.let { dateTimeFormatter.format(it) }
                },
                eta = if (canceled) {
                    when (flight) {
                        is Flight -> { flight.plan.estimatedTime?.end?.toJavaInstant()?.let { dateTimeFormatter.format(it) } }
                        else -> { null }
                    }
                } else {
                    flight.time?.end?.toJavaInstant()?.let { dateTimeFormatter.format(it) }
                },
                delayTime = flight.delay.toString(),
                canceled = canceled,
                straightened = false,   // todo: if implement straight flight
                transfer = flight.type is TransferFlightFlightTask,
                additional = false,     // todo: if implement additional flight
                delayed = if (canceled) {
                    false
                } else {
                    flight.delay != Duration.ZERO
                },
                advanced = if (canceled) {
                    false
                } else {
                    flight.advance != Duration.ZERO
                },
                aircraftChanged = if (canceled) {
                    false
                } else {
                    flight.aircraftChanged
                },
                aircraftMinorTypeChanged = if (canceled) {
                    false
                } else {
                    flight.aircraftMinorTypeChanged
                }
            )
        )
    }

    private fun dumpMaintenance(recoveryPlan: RecoveryPlan, maintenance: FlightTask, canceled: Boolean): Result<RecoveryedMaintenanceDTO, Error> {
        return Ok(
            RecoveryedMaintenanceDTO(
                recoveryPlanId = recoveryPlan.id,
                id = maintenance.actualId,
                acReg = maintenance.aircraft!!.regNo,
                scheduledBeginTime = maintenance.plan.scheduledTime?.begin.toString(),
                scheduledEndTime = maintenance.plan.scheduledTime?.end.toString(),
                scheduledAirport = maintenance.plan.dep.icao,
                estimatedBeginTime = if (canceled) {
                    null
                } else {
                    maintenance.time?.begin.toString()
                },
                estimatedEndTime = if (canceled) {
                    null
                } else {
                    maintenance.time?.end.toString()
                },
                estimatedAirport = if (canceled) {
                    null
                } else {
                    maintenance.dep.icao
                },
                canceled = canceled,
                delayed = if (canceled) {
                    false
                } else {
                    maintenance.delay != Duration.ZERO
                },
                airportChanged = if (canceled) {
                    false
                } else {
                    maintenance.routeChanged
                }
            )
        )
    }
}
