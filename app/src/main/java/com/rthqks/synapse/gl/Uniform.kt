package com.rthqks.synapse.gl

class Uniform<T>(
    val type: Type<T>,
    val name: String,
    val location: Int,
    var data: T? = null,
    var dirty: Boolean = true
) {

    sealed class Type<T> {
        object Int: Type<kotlin.Int>()
        object Float: Type<kotlin.Float>()
        object Mat4 : Type<FloatArray>()
        object Vec2 : Type<FloatArray>()
    }
}