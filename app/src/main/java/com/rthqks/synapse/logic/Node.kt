package com.rthqks.synapse.logic

class Node(
    val type: NodeType
) {
    var networkId: Int = -1
    var id: Int = -1

    val ports = mutableMapOf<String, Port>()
    val properties = Properties()
    val producer: Boolean get() = type.flags and NodeType.FLAG_PRODUCER == NodeType.FLAG_PRODUCER
    val consumer: Boolean get() = type.flags and NodeType.FLAG_CONSUMER == NodeType.FLAG_CONSUMER

    // TODO: Remove this, push to VM/UI
    var position: Int = 0

    fun add(port: Port) {
        ports[port.id] = port
    }

    fun <T> add(property: Property<T>, converter: Converter<T>) {
        properties.put(property, converter)
    }

    fun copy(networkId: Int = this.networkId, id: Int = this.id): Node = Node(type).also {
        it.networkId = networkId
        it.id = id
        it.ports.putAll(ports)
        properties.copyTo(it.properties)
    }

    fun getPort(id: String): Port = ports[id]!!

    fun getPortIds(): Set<String> = ports.keys

    override fun toString(): String {
        return "Node(type=${type.key}, id=$id, networkId=$networkId, position=$position)"
    }

    companion object {
    }
}