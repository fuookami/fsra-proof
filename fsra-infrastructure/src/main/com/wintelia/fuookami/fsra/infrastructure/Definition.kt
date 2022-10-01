package com.wintelia.fuookami.fsra.infrastructure

import java.time.temporal.*
import kotlinx.datetime.*
import java.time.format.DateTimeFormatter

val shortTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("mmddHHMM")

fun Instant.toShortString() = shortTimeFormatter.format(this.toJavaInstant())

fun parseDateTime(str: String): Instant {
    return Instant.parse(str).toJavaInstant().truncatedTo(ChronoUnit.MINUTES).toKotlinInstant()
}
