package com.wintelia.fuookami.fsra

import java.io.*
import java.nio.file.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.io.*
import com.wintelia.fuookami.fsra.infrastructure.*
import com.wintelia.fuookami.fsra.application.frapt.*

fun main(args: Array<String>) {
    val file = File(args[0])
    if (file.isDirectory) {
        val input = read(file.absolutePath)
        val configuration = Configuration()
        val parameter = Parameter(input.parameter)
        val output = run(input, configuration, parameter)
        File("${file.absolutePath}/output.json").writeText(Json.encodeToString(Output.serializer(), output))
    }
}

fun run(input: Input, configuration: Configuration, parameter: Parameter): Output {
    val app = FlightRecoveryAlgorithmPassengerTransport()
    return app(input, configuration, parameter)
}
