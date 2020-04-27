package com.rthqks.synapse.logic

import android.net.Uri
import android.util.Size
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.runBlocking
import java.util.*

class Properties {
    private val properties = mutableMapOf<Property.Key<*>, Property<Any?>>()
    private val channel = BroadcastChannel<Property<*>>(10)
    val size: Int = properties.size

    fun channel() = channel.openSubscription()

    fun <T> put(property: Property<T>) {
        properties[property.key] = property as Property<Any?>
    }

    fun remove(property: Property<out Any?>) {
        properties.remove(property.key)
    }

    fun getAll(): List<Property<*>> = properties.entries.fold(ArrayList(size)) { list, entry ->
        list.add(entry.value)
        list
    }

    fun <T> getProperty(key: Property.Key<T>): Property<T>? = properties[key] as? Property<T>

    operator fun <T> get(key: Property.Key<T>): T = properties[key]?.value as T

    operator fun <T> set(key: Property.Key<T>, value: T) {
        val property = properties.getOrPut(key) { Property(key, value) as Property<Any?> }
        property.value = value
        runBlocking { channel.send(property) }
    }

    fun <T> putString(key: Property.Key<T>, value: String) {
        val cast = when (key.klass) {
            Int::class.java -> value.toInt()
            Float::class.java -> value.toFloat()
            Boolean::class.java -> value.toBoolean()
            Size::class.java -> Size.parseSize(value)
            Uri::class.java -> Uri.parse(value)
            FloatArray::class.java -> value.split(",").map { it.toFloat() }.toFloatArray()
            else -> error("unhandled property type: ${key.klass}")
        } as T
        set(key, cast)
    }

    operator fun plusAssign(properties: Properties) {
        this.properties += properties.properties
    }

    operator fun plus(properties: Properties) = Properties().also {
        it.properties += this.properties
        it.properties += properties.properties
    }

    fun fromString(type: Int, keyName: String, value: String?) {
        if (value != null) {
            putString(getKey(type, keyName), value)
        }
    }

    fun getKey(type: Int, name: String): Property.Key<Any?> = when(type) {
        TYPE_INT -> Property.Key(name, Int::class.java)
        TYPE_FLOAT -> Property.Key(name, Float::class.java)
        TYPE_BOOL -> Property.Key(name, Boolean::class.java)
        TYPE_SIZE -> Property.Key(name, Size::class.java)
        TYPE_URI -> Property.Key(name, Uri::class.java)
        TYPE_FLOAT_ARRAY -> Property.Key(name, FloatArray::class.java)
        else -> error("unhandled property type: ${type}")
    } as Property.Key<Any?>

    companion object {
        const val TYPE_INT = 0
        const val TYPE_FLOAT = 1
        const val TYPE_BOOL = 2
        const val TYPE_SIZE = 3
        const val TYPE_URI = 4
        const val TYPE_FLOAT_ARRAY = 5
    }
}

data class Property<T>(val key: Key<T>, var value: T, var exposed: Boolean = false) {
    data class Key<T>(val name: String, val klass: Class<T>)

    fun getType(): Int = when(key.klass) {
        Int::class.java -> Properties.TYPE_INT
        Float::class.java -> Properties.TYPE_FLOAT
        Boolean::class.java -> Properties.TYPE_BOOL
        Size::class.java -> Properties.TYPE_SIZE
        Uri::class.java -> Properties.TYPE_URI
        FloatArray::class.java -> Properties.TYPE_FLOAT_ARRAY
        else -> error("unhandled property type: ${key.klass}")
    }


    fun getString(): String {
//        if (key !in properties) return ""
        return when (key.klass) {
            Size::class.java -> {
                val p = value as Size
                "${p.width}x${p.height}"
            }
            FloatArray::class.java -> {
                val p = value as FloatArray
                p.joinToString()
            }
            else -> value.toString()
        }
    }
}