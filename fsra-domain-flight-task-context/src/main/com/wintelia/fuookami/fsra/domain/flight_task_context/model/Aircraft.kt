package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import kotlin.time.*
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.concept.*
import com.wintelia.fuookami.fsra.infrastructure.*

enum class AircraftCategory {
    Passenger,
    Cargo
}

sealed class AircraftCapacity {
    class Passenger(
        private val capacity: Map<PassengerClass, UInt64>
    ) : AircraftCapacity() {
        operator fun get(cls: PassengerClass) = capacity[cls] ?: UInt64.zero

        fun enabled(payload: Map<PassengerClass, UInt64>) = payload.asSequence().all { this[it.key] >= it.value }

        override val category get() = AircraftCategory.Passenger
    }

    class Cargo(
        val capacity: Flt64
    ) : AircraftCapacity() {
        override val category get() = AircraftCategory.Cargo

        fun enabled(payload: Flt64) = capacity geq payload
    }

    abstract val category: AircraftCategory
}

data class AircraftType internal constructor(
    val code: AircraftTypeCode
) {
    companion object {
        private val pool = HashMap<AircraftTypeCode, AircraftType>()

        operator fun invoke(code: AircraftTypeCode): AircraftType {
            pool[code] = AircraftType(code)
            return pool[code]!!
        }
    }

    override fun hashCode(): Int {
        return code.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AircraftType

        if (code != other.code) return false

        return true
    }
}

operator fun Map<Route, Duration>.get(dep: Airport, arr: Airport): Duration? {
    return this[Route(dep, arr)]
}

data class AircraftMinorType internal constructor(
    val type: AircraftType,
    val code: AircraftMinorTypeCode,
    val costPerHour: Flt64,
    val routeFlyTime: Map<Route, Duration>,
    val connectionTime: Map<Airport, Duration>
) {
    val maxRouteFlyType: Duration = routeFlyTime.asSequence().maxOf { it.value }
    val maxConnectionTime: Duration = connectionTime.asSequence().maxOf { it.value }

    companion object {
        private val pool = HashMap<AircraftMinorTypeCode, AircraftMinorType>()

        operator fun invoke(
            type: AircraftType,
            code: AircraftMinorTypeCode,
            standardConnectionTime: Map<Airport, Duration>
        ): AircraftMinorType {
            pool[code] = AircraftMinorType(type, code, standardConnectionTime)
            return pool[code]!!
        }

        operator fun invoke(code: AircraftMinorTypeCode) = pool[code]
    }

    override fun hashCode(): Int {
        return type.hashCode().inv() or code.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AircraftMinorType

        if (type != other.type) return false
        if (code != other.code) return false

        return true
    }
}

class Aircraft(
    val regNo: AircraftRegisterNumber,
    val minorType: AircraftMinorType,
    val capacity: AircraftCapacity
) : ManualIndexed() {
    val type by minorType::type
    val costPerHour by minorType::costPerHour
    val routeFlyTime by minorType::routeFlyTime
    val maxRouteFlyTime by minorType::maxRouteFlyType
    val connectionTime by minorType::connectionTime
    val maxConnectionTime by minorType::maxConnectionTime

    companion object {
        private val pool = HashMap<AircraftRegisterNumber, Aircraft>()

        operator fun invoke(
            regNo: AircraftRegisterNumber,
            minorType: AircraftMinorType,
            capacity: AircraftCapacity
        ): Aircraft {
            pool[regNo] = Aircraft(regNo, minorType, capacity)
            return pool[regNo]!!
        }

        operator fun invoke(regNo: AircraftRegisterNumber) = pool[regNo]
    }
}

data class FlightHour(
    val hours: Duration
) {
    companion object {
        val zero = FlightHour(Duration.ZERO)
    }

    operator fun plus(rhs: FlightHour) = FlightHour(hours + rhs.hours)
    operator fun minus(rhs: FlightHour) = FlightHour(hours - rhs.hours)
    infix fun leq(rhs: FlightHour) = hours <= rhs.hours
}

data class FlightCycle(
    val cycles: UInt64
) {
    companion object {
        val zero = FlightCycle(UInt64.zero)
    }

    operator fun plus(rhs: FlightCycle) = FlightCycle(cycles + rhs.cycles)
    operator fun minus(rhs: FlightCycle) = FlightCycle(cycles - rhs.cycles)
    infix fun leq(rhs: FlightCycle) = cycles leq rhs.cycles
}

data class FlightCyclePeriod(
    val expirationTime: Instant,
    val remainingFlightHour: FlightHour,
    val remainingFlightCycle: FlightCycle
)

data class AircraftUsability(
    val lastTask: FlightTask?,
    val location: Airport,
    val enabledTime: Instant,
    val flightCyclePeriods: List<FlightCyclePeriod> = emptyList()
)
