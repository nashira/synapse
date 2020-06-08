import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

data class D(val v: String) {
    operator fun times(d: D): D {
        return D("(${v}*${d.v})")
    }
    operator fun plus(d: D): D {
        return D("(${v}+${d.v})")
    }
    operator fun unaryPlus(): D {
        return D("(+${v})")
    }
    operator fun get(index: Int, prefix: String): D {
        return D(v[index].toString())
    }
}

class Network {
    operator fun get(i: Int, p: String): Port {
        return Port()
    }

    operator fun set(id: Int, type: String) {

    }
}

class Port {
    infix fun linkTo(port: Port) {

    }
}

operator fun String.invoke(id: Int) {
    println("$this($id)")
}

fun fdg() {
    val a = D("a")
    val b = D("b")
    val c = D("c")
    val m = a * b + c * +c
    println(m)

    val network = Network()
    network[1] = "type1"
    network[1, "foo"] linkTo network[2, "bar"]
    "type"(12)

//    val n = Network {

//    }
}

fdg()
