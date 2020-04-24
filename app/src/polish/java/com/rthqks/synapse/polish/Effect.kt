package com.rthqks.synapse.polish

import com.rthqks.synapse.logic.*

class Effect(
    val network: Network,
    val title: String
) {

    val videoOut: Pair<Int, String>
    get() {
        network.ports.forEach { entry ->
            entry.value.forEach {
                if (it.output && it.type == PortType.Video) {
                    return Pair(entry.key, it.id)
                }
            }
        }
        error("missing exposed video port")
    }

    private val propertyTypes = mutableMapOf<Property.Key<*>, PropertyHolder<Any?>>()
    val properties = Properties()

    fun <T> addProperty(property: Property<T>, holder: PropertyHolder<T>) {
        properties.put(property)
        propertyTypes[property.key] = holder as PropertyHolder<Any?>
    }

    fun getProperties() = properties.getAll().map { Pair(it, propertyTypes[it.key]!!) }
}