package com.rthqks.synapse.polish

import com.rthqks.synapse.logic.*
import com.rthqks.synapse.ui.NodeUi
import com.rthqks.synapse.ui.PropertyHolder

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

//    private val propertyTypes = mutableMapOf<Property.Key<*>, PropertyHolder<Any?>>()
//    val properties = Properties()
//
//    fun <T> addProperty(property: Property<T>, holder: PropertyHolder<T>) {
//        properties.put(property)
//        propertyTypes[property.key] = holder as PropertyHolder<Any?>
//    }

    fun getProperties(): MutableList<Pair<Property<*>, PropertyHolder<*>>> {
        val list = mutableListOf<Pair<Property<*>, PropertyHolder<*>>>()
        network.properties.forEach { entry ->
            val node = network.getNode(entry.key)!!
            entry.value.forEach {  p ->
                NodeUi[node.type][p.key]?.let {
                    list += Pair(p, it)
                }
            }
        }
        return list
    }
}