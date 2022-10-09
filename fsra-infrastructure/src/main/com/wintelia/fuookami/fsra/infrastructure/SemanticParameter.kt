package com.wintelia.fuookami.fsra.infrastructure

import kotlinx.serialization.*

// Chinese
@JvmInline
@Serializable
value class CString(val str: String) {
    override fun toString() = str
}

@JvmInline
@Serializable
value class IATA(val code: String) {
    init {
        assert(code.length == 3)
    }

    override fun toString() = code
}

@JvmInline
@Serializable
value class ICAO(val code: String) {
    init {
        assert(code.length == 4)
    }

    override fun toString() = code
}

@JvmInline
@Serializable
value class AircraftTypeName(val name: String) {
    override fun toString() = name
}

@JvmInline
@Serializable
value class AircraftTypeCode(val code: String) {
    override fun toString() = code
}

@JvmInline
@Serializable
value class AircraftMinorTypeName(val name: String) {
    override fun toString() = name
}

@JvmInline
@Serializable
value class AircraftMinorTypeCode(val code: String) {
    override fun toString() = code
}

@JvmInline
@Serializable
value class WingAircraftTypeCode(val code: String) {
    override fun toString() = code
}

@JvmInline
@Serializable
value class AircraftRegisterNumber(val no: String) {
    override fun toString() = no
}
