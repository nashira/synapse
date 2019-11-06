package xyz.rthqks.synapse.gl

class Uniform<T>(
    val type: Type<T>,
    val name: String,
    val location: Int,
    var data: T? = null,
    var dirty: Boolean = true
) {

    sealed class Type<T> {
        object Integer: Type<Int>()
        object Float: Type<Float>()
        object Mat4 : Type<FloatArray>()
    }
}