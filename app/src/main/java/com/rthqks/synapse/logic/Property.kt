package com.rthqks.synapse.logic

class Properties {
    private val map = mutableMapOf<Property.Key<*>, Property<*>>()
    private val converters = mutableMapOf<Property.Key<*>, Converter<*>>()
    val size: Int = map.size
    val keys = map.keys

    fun <T> put(property: Property<T>, converter: Converter<T>) {
        val key = property.key
        map[key] = property
        converters[key] = converter
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> set(key: Property.Key<T>, value: T) {
        (map[key] as? Property<T>)?.value = value
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T> get(key: Property.Key<T>): T {
        return map[key]?.value as T
    }

    @Suppress("UNCHECKED_CAST")
    operator fun set(keyName: String, value: String?) {
        value ?: return

//        val key = keys.first { it.name == keyName }
        val key = Property.Key<Any?>(keyName)
        val converter = converters[key]

        (map[key] as? Property<Any?>)?.let { property ->
            converter?.let {
                property.value = it.fromString(value)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> toString(key: Property.Key<T>): String? {
        return (converters[key] as? Converter<T>)?.toString(this[key])
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> find(key: Property.Key<T>): Property<T>? {
        return map[key] as? Property<T>
    }

    operator fun plus(other: Properties): Properties = Properties().also {
        it += this
        it += other
    }

    operator fun plusAssign(properties: Properties) {
        map.putAll(properties.map)
        converters.putAll(properties.converters)
    }

    fun copyTo(properties: Properties): Properties = properties.also {
        map.mapValuesTo(it.map) { entry ->
            entry.value.copy()
        }
        properties.converters.putAll(converters)
    }
}

data class Property<T>(
    val key: Key<T>,
    val type: PropertyType<T>,
    var value: T,
    val requiresRestart: Boolean = false
) {
    data class Key<T>(val name: String)
}
