package com.rthqks.synapse.logic

class Node(
    val type: String,
    var id: Int = -1
) {
    var networkId: Int = -1

    val ports = mutableMapOf<String, Port>()
    val properties = Properties()

    fun add(port: Port) {
        ports[port.id] = port
    }

    fun <T> add(key: Property.Key<T>, value: T) {
        properties[key] = value
    }

    fun copy(networkId: Int = this.networkId, id: Int = this.id): Node = Node(type).also {
        it.networkId = networkId
        it.id = id
        it.ports.putAll(ports)
        properties.getAll().forEach { p ->
            it.properties[p.key as Property.Key<Any?>] = p.value
        }
    }

    fun getPort(id: String): Port = ports[id] ?: error("unknown port $id for $type")

    fun getPortIds(): Set<String> = ports.keys

    override fun toString(): String {
        return "Node(type=${type}, id=$id, networkId=$networkId)"
    }

    companion object {
    }
}