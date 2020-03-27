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

    fun <T> getString(key: Property.Key<T>): String {
//        if (key !in properties) return ""
        return when (key.klass) {
            Size::class.java -> {
                val p = properties[key]?.value as Size
                "${p.width}x${p.height}"
            }
            else -> properties[key]?.value.toString()
        }
    }

    fun <T> putString(key: Property.Key<T>, value: String) {
        val cast = when (key.klass) {
            Int::class.java -> value.toInt()
            Float::class.java -> value.toFloat()
            Boolean::class.java -> value.toBoolean()
            Size::class.java -> Size.parseSize(value)
            Uri::class.java -> Uri.parse(value)
            else -> error("unhandled property type: ${key.klass}")
        } as T
        set(key, cast)
    }

    operator fun plusAssign(properties: Properties) {
        this.properties += properties.properties
    }

    operator fun set(keyName: String, value: String?) {
        if (value != null) {
            putString(KeyMap[keyName] as Property.Key<Any?>, value)
        }
    }
}

data class Property<T>(val key: Key<T>, var value: T) {
    data class Key<T>(val name: String, val klass: Class<T>)
}