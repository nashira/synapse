package com.rthqks.synapse.logic

import android.net.Uri
import android.util.Size
import kotlin.reflect.KClass

data class Property(
    val networkId: Int,
    val nodeId: Int,
    val key: Key<*>,
    var value: Any,
    var exposed: Boolean = false
) {
    val type: Int get() = getType(key.klass)
    val stringValue: String get() = getString(key.klass, value)

    data class Key<T: Any>(val name: String, val klass: KClass<T>)

    companion object {
        const val TYPE_INT = 0
        const val TYPE_FLOAT = 1
        const val TYPE_BOOL = 2
        const val TYPE_SIZE = 3
        const val TYPE_URI = 4
        const val TYPE_FLOAT_ARRAY = 5

        fun <T: Any> fromString(klass: KClass<T>, value: String): T = when (klass) {
            Int::class -> value.toInt()
            Float::class -> value.toFloat()
            Boolean::class -> value.toBoolean()
            Size::class -> Size.parseSize(value)
            Uri::class -> Uri.parse(value)
            FloatArray::class -> value.split(",").map { it.toFloat() }.toFloatArray()
            else -> error("unhandled property type: ${klass}")
        } as T

        fun getKey(type: Int, name: String): Key<Any> = when (type) {
            TYPE_INT -> Key(name, Int::class)
            TYPE_FLOAT -> Key(name, Float::class)
            TYPE_BOOL -> Key(name, Boolean::class)
            TYPE_SIZE -> Key(name, Size::class)
            TYPE_URI -> Key(name, Uri::class)
            TYPE_FLOAT_ARRAY -> Key(name, FloatArray::class)
            else -> error("unhandled property type: ${type}")
        } as Key<Any>

        fun getType(klass: KClass<*>): Int = when (klass) {
            Int::class -> TYPE_INT
            Float::class -> TYPE_FLOAT
            Boolean::class -> TYPE_BOOL
            Size::class -> TYPE_SIZE
            Uri::class -> TYPE_URI
            FloatArray::class -> TYPE_FLOAT_ARRAY
            else -> error("unhandled property type: ${klass}")
        }

        fun <T: Any> getString(klass: KClass<T>, value: Any): String {
//        if (key !in properties) return ""
            return when (klass) {
                Size::class -> {
                    val p = value as Size
                    "${p.width}x${p.height}"
                }
                FloatArray::class -> {
                    val p = value as FloatArray
                    p.joinToString()
                }
                else -> value.toString()
            }
        }
    }
}