package com.wintelia.fuookami.fsra.io

import kotlinx.serialization.*
import kotlinx.serialization.builtins.*
import kotlinx.serialization.csv.*

fun <T> readCSVFile(serializer: KSerializer<T>, path: String): List<T> {
    val csv = Csv { hasHeaderRecord = true }
    return csv.decodeFromString(
        ListSerializer(serializer),
        readChineseFile(path)
    )
}
