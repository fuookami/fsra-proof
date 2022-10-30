package com.wintelia.fuookami.fsra.domain.bunch_generation_context.service

import kotlin.time.*
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*

class InitialFlightTaskBunchGenerator(
    val feasibilityJudger: FlightTaskFeasibilityJudger,
    val connectionTimeCalculator: ConnectionTimeCalculator,
    val minimumDepartureTimeCalculator: MinimumDepartureTimeCalculator,
    val costCalculator: TotalCostCalculator
) {
    companion object {
        val config = FlightTaskFeasibilityJudger.Config(
            timeExtractor = FlightTask::time
        )
    }

    operator fun invoke(aircraft: Aircraft, aircraftUsability: AircraftUsability, lockedFlightTasks: List<FlightTask>, originBunch: FlightTaskBunch): FlightTaskBunch? {
        return softRecovery(aircraft, aircraftUsability, lockedFlightTasks, originBunch)
            ?: emptyBunch(aircraft, aircraftUsability, lockedFlightTasks)
    }

    fun emptyBunch(aircraft: Aircraft, aircraftUsability: AircraftUsability, lockedFlightTasks: List<FlightTask>): FlightTaskBunch? {
        if (lockedFlightTasks.isEmpty()) {
            return null
        }

        val flightTasks = recoveryFlightTasks(aircraft, aircraftUsability, lockedFlightTasks)
        return if (flightTasks.isEmpty()) {
            null
        } else {
            val cost = costCalculator(aircraft, flightTasks)
            if (cost == null || !cost.valid) {
                null
            } else {
                FlightTaskBunch(aircraft, aircraftUsability, flightTasks, UInt64.zero, cost)
            }
        }
    }

    private fun softRecovery(aircraft: Aircraft, aircraftUsability: AircraftUsability, lockedFlightTasks: List<FlightTask>, originBunch: FlightTaskBunch): FlightTaskBunch? {
        val flightTasks = if (lockedFlightTasks.isEmpty()) {
            recoveryFlightTasks(aircraft, aircraftUsability, originBunch.flightTasks)
        } else {
            var currentLocation = aircraftUsability.location
            val lastFlightTask = aircraftUsability.lastTask
            var currentTime = if (lastFlightTask != null) {
                lastFlightTask.time!!.end
            } else {
                aircraftUsability.enabledTime
            }

            val softFlightTasks = ArrayList<FlightTask>()
            val insertedFlightTasks = HashSet<FlightTask>()

            for (lockedFlightTask in lockedFlightTasks) {
                val prevFlightTask = if (softFlightTasks.isEmpty()) {
                    lastFlightTask
                } else {
                    softFlightTasks.last()
                }

                var flag = false
                for (flightTask in originBunch.flightTasks) {
                    if (insertedFlightTasks.contains(flightTask)) {
                        continue
                    }

                    val connectionTime = if (prevFlightTask != null) {
                        connectionTimeCalculator(aircraft, prevFlightTask, flightTask)
                    } else {
                        Duration.ZERO
                    }
                    val departureTime = minimumDepartureTimeCalculator(currentTime, aircraft, flightTask, connectionTime)
                    val actualTime = TimeRange(departureTime, departureTime + connectionTime)
                    val recoveryPolicy = if (actualTime == flightTask.scheduledTime!!) {
                        RecoveryPolicy()
                    } else {
                        RecoveryPolicy(time = actualTime)
                    }
                    val recoveryedFlightTask = if (recoveryPolicy.empty) {
                        flightTask
                    } else if (flightTask.recoveryEnabled(recoveryPolicy)) {
                        flightTask.recovery(recoveryPolicy)
                    } else {
                        null
                    }

                    if (recoveryedFlightTask != null) {
                        if (currentLocation == recoveryedFlightTask.dep && recoveryedFlightTask.arr == lockedFlightTask.dep) {
                            val thisConnectionTime = connectionTimeCalculator(aircraft, recoveryedFlightTask, lockedFlightTask)
                            val thisDepartureTime = minimumDepartureTimeCalculator(actualTime.end, aircraft, lockedFlightTask, thisConnectionTime)
                            if (thisDepartureTime == lockedFlightTask.time!!.begin) {
                                flag = true
                                softFlightTasks.add(flightTask)
                                softFlightTasks.add(lockedFlightTask)
                                currentTime = lockedFlightTask.time!!.end
                                currentLocation = lockedFlightTask.arr
                                break
                            }
                        } else if (currentLocation == flightTask.dep) {
                            softFlightTasks.add(flightTask)
                            currentTime = actualTime.end
                            currentLocation = flightTask.arr
                            insertedFlightTasks.add(flightTask)
                        }
                    }
                }

                if (!flag && currentLocation == lockedFlightTask.dep) {
                    softFlightTasks.add(lockedFlightTask)
                    currentTime = lockedFlightTask.time!!.end
                    currentLocation = lockedFlightTask.arr
                }
            }

            for (flightTask in originBunch.flightTasks) {
                if (insertedFlightTasks.contains(flightTask)) {
                    continue
                }

                val prevFlightTask = if (softFlightTasks.isEmpty()) {
                    lastFlightTask
                } else {
                    softFlightTasks.last()
                }

                val connectionTime = if (prevFlightTask != null) {
                    connectionTimeCalculator(aircraft, prevFlightTask, flightTask)
                } else {
                    Duration.ZERO
                }
                val departureTime = minimumDepartureTimeCalculator(currentTime, aircraft, flightTask, connectionTime)
                val actualTime = TimeRange(departureTime, departureTime + connectionTime)
                val recoveryPolicy = if (actualTime == flightTask.scheduledTime!!) {
                    RecoveryPolicy()
                } else {
                    RecoveryPolicy(time = actualTime)
                }
                val recoveryedFlightTask = if (recoveryPolicy.empty) {
                    flightTask
                } else if (flightTask.recoveryEnabled(recoveryPolicy)) {
                    flightTask.recovery(recoveryPolicy)
                } else {
                    null
                }

                if (recoveryedFlightTask != null && currentLocation == flightTask.dep) {
                    softFlightTasks.add(flightTask)
                    insertedFlightTasks.add(flightTask)
                    currentTime = actualTime.end
                    currentLocation = flightTask.arr
                }
            }

            recoveryFlightTasks(aircraft, aircraftUsability, softFlightTasks)
        }

        if (flightTasks.isEmpty()) {
            return null
        }

        val cost = costCalculator(aircraft, flightTasks)
        return if (cost == null || !cost.valid) {
            null
        } else {
            FlightTaskBunch(aircraft, aircraftUsability, flightTasks, UInt64.zero, cost)
        }
    }

    private fun recoveryFlightTasks(aircraft: Aircraft, aircraftUsability: AircraftUsability, lockedFlightTasks: List<FlightTask>): List<FlightTask> {
        if (aircraft.regNo.no == "1661") {
            println("1")
        }

        val flightTasks = ArrayList<FlightTask>()
        if (lockedFlightTasks.isEmpty()) {
            return flightTasks
        }

        val lastFlightTask = aircraftUsability.lastTask
        var time = if (lastFlightTask != null) {
            lastFlightTask.time!!.end
        } else {
            aircraftUsability.enabledTime
        }
        for (i in lockedFlightTasks.indices) {
            val flightTask = lockedFlightTasks[i]
            val prevFlightTask = if (flightTasks.isEmpty()) {
                lastFlightTask
            } else {
                flightTasks.last()
            }

            val connectionTime = if (prevFlightTask != null) {
                connectionTimeCalculator(aircraft, prevFlightTask, flightTask)
            } else {
                Duration.ZERO
            }
            time = minimumDepartureTimeCalculator(time, aircraft, flightTask, connectionTime)
            val recoveryedTime = TimeRange(time, time + flightTask.duration(aircraft))
            val recoveryPolicy = if (recoveryedTime == flightTask.scheduledTime!!) {
                RecoveryPolicy()
            } else {
                RecoveryPolicy(time = recoveryedTime)
            }

            val recoveryedFlightTask = if (recoveryPolicy.empty) {
                flightTask
            } else if (flightTask.recoveryEnabled(recoveryPolicy)) {
                flightTask.recovery(recoveryPolicy)
            } else {
                null
            }

            if (recoveryedFlightTask != null) {
                if (!feasibilityJudger(aircraft, prevFlightTask, recoveryedFlightTask, config)) {
                    continue
                }
                flightTasks.add(recoveryedFlightTask)
                time += recoveryedFlightTask.duration(aircraft)
            }
        }

        return flightTasks
    }

    private fun feasible(lastFlight: FlightTask?, bunch: FlightTaskBunch): Boolean {
        for (i in bunch.flightTasks.indices) {
            val prevFlightTask = if (i == 0) {
                bunch.lastTask
            } else {
                bunch.flightTasks[i - 1]
            }

            if (!feasibilityJudger(bunch.aircraft, prevFlightTask, bunch.flightTasks[i], config)) {
                return false
            }
        }
        return true
    }
}
