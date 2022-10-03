package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import kotlin.math.*
import com.wintelia.fuookami.fsra.infrastructure.*

enum class FlightType {
    // 国内
    Domestic {
        override val isDomainType: Boolean get() = true
        override fun toChineseString() = CString("国内")
    },

    // 区域
    Regional {
        override fun toChineseString() = CString("区域")
    },

    // 国际
    International {
        override fun toChineseString() = CString("国际")
    };

    companion object {
        operator fun invoke(str: CString): FlightType? {
            return FlightType.values().find { it.toChineseString() == str }
        }

        operator fun invoke(dep: Airport, arr: Airport): FlightType {
            return invoke(dep.type, arr.type)
        }

        operator fun invoke(dep: AirportType, arr: AirportType): FlightType {
            return when (AirportType.values().find { it.ordinal == max(dep.ordinal, arr.ordinal) }!!) {
                AirportType.Domestic -> {
                    Domestic
                }

                AirportType.Regional -> {
                    Regional
                }

                AirportType.International -> {
                    International
                }
            }
        }
    }

    open val isDomainType: Boolean get() = false
    abstract fun toChineseString(): CString
}

data class FlightPlan(
    val type: FlightType,
) : FlightTaskPlan() {

}

class Flight(
    val plan: FlightPlan
) : FlightTask(plan) {
    val type by plan::type
}
