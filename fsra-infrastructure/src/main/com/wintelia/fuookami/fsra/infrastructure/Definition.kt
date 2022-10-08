package com.wintelia.fuookami.fsra.infrastructure

import java.time.temporal.*
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import java.time.format.DateTimeFormatter

val shortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("mmdd")
val shortTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("mmddHHMM")

fun Instant.toShortString(): String = shortTimeFormatter.format(this.toJavaInstant())

data class Date(
    val value: Instant
) {
    companion object {
        operator fun invoke(value: Instant): Date {
            return Date(value.toJavaInstant().truncatedTo(ChronoUnit.DAYS).toKotlinInstant())
        }
    }

    fun localDate(timeZone: TimeZone = TimeZone.currentSystemDefault()) = value.toLocalDateTime(timeZone).date

    fun toShortString(): String = shortDateFormat.format(value.toJavaInstant())
}

fun parseDate(str: String): Date {
    return Date(Instant.parse(str).toJavaInstant().truncatedTo(ChronoUnit.DAYS).toKotlinInstant())
}

fun parseDateTime(str: String): Instant {
    return Instant.parse(str).toJavaInstant().truncatedTo(ChronoUnit.MINUTES).toKotlinInstant()
}
