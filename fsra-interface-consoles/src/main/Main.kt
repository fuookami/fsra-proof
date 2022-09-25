import com.wintelia.fuookami.fsra.infrastructure.dto.*
import com.wintelia.fuookami.fsra.io.readCSVFile
import kotlinx.serialization.*

fun main() {
    val input = readCSVFile(AirportDTO.serializer(), "E:\\WorkSpace\\Project\\wintelia\\data1\\input\\airports.txt")
    print(input)
}
