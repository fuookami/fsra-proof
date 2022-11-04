package com.wintelia.fuookami.fsra.domain.flight_task_context.model

import fuookami.ospf.kotlin.utils.concept.*

enum class PassengerClass: Indexed {
    First {
        override fun toShortString() = "F"
    },
    Business {
        override fun toShortString() = "B"
    },
    Economy {
        override fun toShortString() = "E"
    };

    abstract fun toShortString(): String
    override val index: Int get() = this.ordinal
}
