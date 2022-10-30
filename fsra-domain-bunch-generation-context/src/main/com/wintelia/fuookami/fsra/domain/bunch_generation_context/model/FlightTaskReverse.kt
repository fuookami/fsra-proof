package com.wintelia.fuookami.fsra.domain.bunch_generation_context.model

import kotlin.time.*
import kotlin.time.Duration.Companion.hours
import fuookami.ospf.kotlin.utils.math.*
import com.wintelia.fuookami.fsra.domain.flight_task_context.model.*
import com.wintelia.fuookami.fsra.domain.rule_context.model.*

class FlightTaskReverse private constructor(
    private val symmetricalPairs: List<ReversiblePair> = ArrayList(),
    private val leftMapper: Map<FlightTaskKey, List<ReversiblePair>>,
    private val rightMapper: Map<FlightTaskKey, List<ReversiblePair>>
) {
    data class ReversiblePair(
        val prevTask: FlightTask,
        val succTask: FlightTask,
        val symmetrical: Boolean
    )

    companion object {
        val defaultTimeDifferenceLimit = 5.hours
        val criticalSize = UInt64(200UL)

        operator fun invoke(pairs: List<Pair<FlightTask, FlightTask>>, originBunches: List<FlightTaskBunch>, lock: Lock, timeDifferenceLimit: Duration): FlightTaskReverse {
            val symmetricalPairs = ArrayList<ReversiblePair>()
            val leftMapper = HashMap<FlightTaskKey, ArrayList<ReversiblePair>>()
            val rightMapper = HashMap<FlightTaskKey, ArrayList<ReversiblePair>>()

            for (pair in pairs) {
                assert(reverseEnabled(pair.first, pair.second, lock, timeDifferenceLimit))

                val reversiblePair = ReversiblePair(pair.first, pair.second, symmetrical(originBunches, pair.first, pair.second, lock, timeDifferenceLimit))
                if (!leftMapper.containsKey(pair.first.key)) {
                    leftMapper[pair.first.key] = ArrayList()
                }
                leftMapper[pair.first.key]!!.add(reversiblePair)
                if (!rightMapper.containsKey(pair.second.key)) {
                    rightMapper[pair.second.key] = ArrayList()
                }
                rightMapper[pair.second.key]!!.add(reversiblePair)

                if (reversiblePair.symmetrical) {
                    symmetricalPairs.add(reversiblePair)
                }
            }
            return FlightTaskReverse(
                symmetricalPairs = symmetricalPairs,
                leftMapper = leftMapper,
                rightMapper = rightMapper
            )
        }

        fun reverseEnabled(prevFlightTask: FlightTask, succFlightTask: FlightTask, lock: Lock, timeDifferenceLimit: Duration): Boolean {
//            if (!prevFlightTask.isFlight || !succFlightTask.isFlight) {
//                return false
//            }
            if (prevFlightTask.dep != succFlightTask.arr) {
                return false
            }
            if (!prevFlightTask.delayEnabled && !succFlightTask.advanceEnabled) {
                return false
            }
            if (lock.lockedTime(prevFlightTask) != null) {
                return false
            }

            val prevScheduledTime = prevFlightTask.scheduledTime
            val succScheduledTime = succFlightTask.scheduledTime
            if (prevScheduledTime != null && succScheduledTime != null
                && prevScheduledTime.begin < succScheduledTime.begin
                && (succScheduledTime.begin - prevScheduledTime.begin) <= timeDifferenceLimit
            ) {
                return true
            }

            val prevTimeWindow = prevFlightTask.timeWindow
            val succTimeWindow = succFlightTask.timeWindow
            if (prevTimeWindow != null && succScheduledTime != null
                && prevTimeWindow.begin < succScheduledTime.begin
                && succScheduledTime.begin < prevTimeWindow.end
            ) {
                return true
            }
            if (prevScheduledTime != null && succTimeWindow != null
                && prevScheduledTime.begin < succTimeWindow.begin
                && (succTimeWindow.begin - prevScheduledTime.begin) <= timeDifferenceLimit
            ) {
                return true
            }
            val prevDuration = prevFlightTask.duration
            val succDuration = succFlightTask.duration
            if (prevTimeWindow != null && succTimeWindow != null
                && prevDuration != null && succDuration != null
                && (prevTimeWindow.end - prevDuration) <= (succTimeWindow.end - succDuration)
            ) {
                return true
            }

            return false
        }

        fun symmetrical(prevFlightTask: FlightTask, succFlightTask: FlightTask, lock: Lock, timeDifferenceLimit: Duration): Boolean {
            return reverseEnabled(prevFlightTask, succFlightTask, lock, timeDifferenceLimit)
                    && prevFlightTask.aircraft == succFlightTask.aircraft
        }

        private fun symmetrical(originBunches: List<FlightTaskBunch>, prevFlightTask: FlightTask, succFlightTask: FlightTask, lock: Lock, timeDifferenceLimit: Duration): Boolean {
            assert(reverseEnabled(prevFlightTask, succFlightTask, lock, timeDifferenceLimit))
            return originBunches.any { it.contains(prevFlightTask, succFlightTask) }
                    && prevFlightTask.arr == succFlightTask.dep
                    && !(prevFlightTask.dep.base && prevFlightTask.arr.base)
        }
    }

    fun contains(prevFlightTask: FlightTask, succFlightTask: FlightTask): Boolean {
        return leftMapper[prevFlightTask.key]?.any { it.succTask == succFlightTask } ?: false
    }

    fun symmetrical(prevFlightTask: FlightTask, succFlightTask: FlightTask): Boolean {
        return leftMapper[prevFlightTask.key]?.find { it.succTask == succFlightTask.originTask }?.symmetrical ?: false
    }

    fun leftFind(flightTask: FlightTask): List<ReversiblePair> {
        return leftMapper[flightTask.key] ?: emptyList()
    }

    fun rightFind(flightTask: FlightTask): List<ReversiblePair> {
        return rightMapper[flightTask.key] ?: emptyList()
    }
}
