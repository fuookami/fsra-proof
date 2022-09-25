package com.wintelia.fuookami.fsra.infrastructure.dto

import kotlinx.serialization.*
import com.wintelia.fuookami.fsra.infrastructure.*

@Serializable
data class AirportDTO(
    @SerialName("airport")
    val airport: ICAO,
    @SerialName("airport_type")
    val type: String
)
