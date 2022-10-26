package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import com.wintelia.fuookami.fsra.infrastructure.*

enum class AirportType {
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
        operator fun invoke(str: CString): AirportType? {
            return AirportType.values().find { it.toChineseString() == str }
        }
    }

    open val isDomainType: Boolean get() = false
    abstract fun toChineseString(): CString
}

data class Airport internal constructor(
    val icao: ICAO,
    val type: AirportType,
    val base: Boolean = false
) {
    companion object {
        private val pool = HashMap<ICAO, Airport>()

        operator fun invoke(icao: ICAO) = pool[icao]
    }

    init {
        pool[icao] = this
    }

    override fun hashCode(): Int {
        assert(icao.code.length == 4 && icao.code.all { it.isUpperCase() })

        var ret = 0
        for (ch in icao.code) {
            ret = ret shl 4
            ret = ret or (ch - 'A')
        }
        return ret
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Airport

        if (icao != other.icao) return false

        return true
    }

    override fun toString() = "$icao"
}

data class Route(
    val dep: Airport,
    val arr: Airport
)

enum class ArrivalAirportScope {
    Master,
    Alternate
}
