package com.wintelia.fuookami.fsra.infrastructure

import kotlin.time.*
import kotlinx.datetime.*
import kotlinx.serialization.*
import fuookami.ospf.kotlin.utils.math.*

// Chinese
@JvmInline
@Serializable
value class CString(val str: String)

@JvmInline
@Serializable
value class IATA(val code: String) {
    init {
        assert(code.length == 3)
    }
}

@JvmInline
@Serializable
value class ICAO(val code: String) {
    init {
        assert(code.length == 4)
    }
}

@JvmInline
@Serializable
value class AircraftTypeName(val name: String)

@JvmInline
@Serializable
value class AircraftTypeCode(val code: String)

@JvmInline
@Serializable
value class AircraftMinorTypeName(val name: String)

@JvmInline
@Serializable
value class AircraftMinorTypeCode(val code: String)

@JvmInline
@Serializable
value class WingAircraftTypeCode(val code: String)

@JvmInline
@Serializable
value class AircraftRegisterNumber(val no: String)
