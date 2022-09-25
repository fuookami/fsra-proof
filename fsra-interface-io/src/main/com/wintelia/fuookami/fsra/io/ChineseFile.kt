package com.wintelia.fuookami.fsra.io

import java.io.*
import java.nio.charset.*

fun readChineseFile(path: String): String {
    return File(path)
        .readText(Charset.forName("GBK"))
        .replace("\r", "")
}
