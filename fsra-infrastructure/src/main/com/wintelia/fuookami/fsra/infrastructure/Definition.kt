package com.wintelia.fuookami.fsra.infrastructure

import java.time.format.*
import java.time.temporal.*
import kotlinx.datetime.*
import fuookami.ospf.kotlin.utils.math.*

val precision = Flt64(1e-5)
val ls = Less<Flt64, Flt64>(precision)
val leq = LessEqual<Flt64, Flt64>(precision)
val gr = Greater<Flt64, Flt64>(precision)
val geq = GreaterEqual<Flt64, Flt64>(precision)
val eq = Equal<Flt64, Flt64>(precision)

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-mm-dd")
private val shortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("mmdd")
private val shortTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("mmddHHMM")

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

    override fun toString(): String = dateFormat.format(value.toJavaInstant())
    fun toShortString(): String = shortDateFormat.format(value.toJavaInstant())
}

fun parseDate(str: String): Date {
    return Date(Instant.parse(str).toJavaInstant().truncatedTo(ChronoUnit.DAYS).toKotlinInstant())
}

fun parseDateTime(str: String): Instant {
    return Instant.parse(str).toJavaInstant().truncatedTo(ChronoUnit.MINUTES).toKotlinInstant()
}
