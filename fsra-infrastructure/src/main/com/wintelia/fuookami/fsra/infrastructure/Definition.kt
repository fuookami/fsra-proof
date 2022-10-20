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

private val shortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMdd").withZone(TimeZone.currentSystemDefault().toJavaZoneId())
private val shortTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMddHHmm").withZone(TimeZone.currentSystemDefault().toJavaZoneId())

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
