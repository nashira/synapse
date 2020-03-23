import java.io.DataOutputStream
import java.io.InputStream
import java.io.File
import java.nio.ByteBuffer

val LUT_1D_PATTERN = Regex("^LUT_1D_SIZE\\s+(\\d+)")
val LUT_3D_PATTERN = Regex("^LUT_3D_SIZE\\s+(\\d+)")
val NUMBER = "[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?"
val TABLE_DATA = Regex("^($NUMBER)\\s+($NUMBER)\\s+($NUMBER)")
val DOMAIN_MIN = Regex("^DOMAIN_MIN\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)")
val DOMAIN_MAX = Regex("^DOMAIN_MAX\\s+([^\\s]+)\\s+([^\\s]+)\\s+([^\\s]+)")

class Cube {
    var n = 0
    var is3d = false
    val min = floatArrayOf(0f, 0f, 0f)
    val max = floatArrayOf(1f, 1f, 1f)

    fun scale(v: Float, i: Int) = (v - min[i]) / (max[i] - min[i])
}

fun convert(cubeFile: String) {
    val stream = File(cubeFile).inputStream()
    val reader = stream.bufferedReader()
    val cube = Cube()

    var output = DataOutputStream(File(cubeFile.replace(".cube", ".bcube")).outputStream())

    var lineCount = 0
    reader.forEachLine { line ->
        when {
            line matches TABLE_DATA -> TABLE_DATA.find(line)?.let {
                lineCount++
                val r = it.groupValues[1].toFloat()
                val g = it.groupValues[2].toFloat()
                val b = it.groupValues[3].toFloat()

                if (r > 1f || g > 1f || b > 1f) {
                    println("rgb($r, $g, $b)")
                }

                val rs = (cube.scale(r, 0) * 255).toInt()
                val gs = (cube.scale(g, 1) * 255).toInt()
                val bs = (cube.scale(b, 2) * 255).toInt()
//                println("rgb($rs, $gs, $bs)")
                output.write(rs)
                output.write(gs)
                output.write(bs)
            }
            line matches DOMAIN_MIN -> DOMAIN_MIN.find(line)?.let {
                cube.min[0] = it.groupValues[1].toFloat()
                cube.min[1] = it.groupValues[2].toFloat()
                cube.min[2] = it.groupValues[3].toFloat()
            }
            line matches DOMAIN_MAX -> DOMAIN_MAX.find(line)?.let {
                cube.max[0] = it.groupValues[1].toFloat()
                cube.max[1] = it.groupValues[2].toFloat()
                cube.max[2] = it.groupValues[3].toFloat()
            }
            line matches LUT_1D_PATTERN -> LUT_1D_PATTERN.find(line)?.let {
                println("found 1D lut ${it.groupValues[1]}")
                cube.n = it.groupValues[1].toInt()
            }
            line matches LUT_3D_PATTERN -> LUT_3D_PATTERN.find(line)?.let {
                println("found 3D lut ${it.groupValues[1]}")
                cube.n = it.groupValues[1].toInt()
                output.writeInt(cube.n)
                cube.is3d = true
            }
        }
    }
    println("lineCount $lineCount bytes written: ${output.size()}")
    stream.close()
    output.close()
}


val dir = File("../assets/cube")
dir.list().forEach {
    if (it.endsWith(".cube")) {
        println(it)
        convert("../assets/cube/$it")
    }
}
