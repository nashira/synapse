package com.rthqks.synapse.polish

import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Properties
import com.rthqks.synapse.logic.Property
import com.rthqks.synapse.logic.PropertyHolder

class Effect(
    val network: Network,
    val title: String,
    val videoOut: Pair<Int, String>
) {
    private val propertyTypes = mutableMapOf<Property.Key<*>, PropertyHolder<Any?>>()
    val properties = Properties()

    fun <T> addProperty(property: Property<T>, holder: PropertyHolder<T>) {
        properties.put(property)
        propertyTypes[property.key] = holder as PropertyHolder<Any?>
    }

    fun getProperties() = properties.getAll().map { Pair(it, propertyTypes[it.key]!!) }
}