package com.wintelia.fuookami.fsra.infrastructure

import java.time.temporal.*
import kotlinx.datetime.*

fun parseDateTime(str: String): LocalDateTime {
    return LocalDateTime.parse(str).toJavaLocalDateTime().truncatedTo(ChronoUnit.MINUTES).toKotlinLocalDateTime()
}
