package com.rthqks.flow.logic

import android.util.Log
import java.util.*
import kotlin.math.max

class Network(
    val id: String,
    val creatorId: String,
    var name: String,
    var description: String = ""
) {
    private val nodes = mutableMapOf<Int, Node>()

    //    private val properties = mutableMapOf<Int, MutableMap<String, Property>>()
//    private val ports = mutableMapOf<Int, MutableMap<String, Port>>()
    private val links = mutableSetOf<Link>()
    private val linkIndex = mutableMapOf<Int, MutableSet<Link>>()
    private var nextNodeId = 1

    // nodes
    fun addNode(nodeDef: NodeDef, id: Int = nextNodeId): Node {
        val node = addNode(nodeDef.key, id)
        nodeDef.ports.forEach { addPort(node.id, it.type, it.key, it.output) }
        nodeDef.properties.forEach { addProperty(node.id, it.key as Property.Key<Any>, it.value) }
        return node
    }

    fun addNode(node: Node, id: Int = nextNodeId): Node {
        val newNode = addNode(node.type, id)
        node.ports.values.forEach { addPort(newNode.id, it.type, it.key, it.output, it.exposed) }
        node.properties.values.forEach {
            addProperty(
                newNode.id,
                it.key as Property.Key<Any>,
                it.value,
                it.exposed
            )
        }
        return newNode
    }

    fun addNode(type: String, id: Int = nextNodeId) = Node(this.id, id, type).also {
        nextNodeId = max(nextNodeId, id) + if (id < nextNodeId) 0 else 1
        nodes[id] = it
    }

    fun removeNode(nodeId: Int) {
        nodes.remove(nodeId)
    }

    fun getNode(nodeId: Int): Node {
//        Log.d(TAG, "getNode $nodeId")
        return nodes[nodeId] ?: error("node not found: $nodeId")
    }

    fun getNodes(): Collection<Node> {
        return nodes.values.toList()
    }

    // links
    fun addLink(fromNodeId: Int, fromPort: String, toNodeId: Int, toPort: String) {
        val link = Link(fromNodeId, fromPort, toNodeId, toPort)
        links.add(link)
        linkIndex.getOrPut(link.fromNodeId) { mutableSetOf() } += link
        linkIndex.getOrPut(link.toNodeId) { mutableSetOf() } += link
    }

    fun removeLink(fromNodeId: Int, fromPort: String, toNodeId: Int, toPort: String) {
        val link = Link(fromNodeId, fromPort, toNodeId, toPort)
        links.remove(link)
        linkIndex[link.fromNodeId]?.remove(link)
        linkIndex[link.toNodeId]?.remove(link)
    }

    fun addLink(link: Link) {
        links.add(link)
        linkIndex.getOrPut(link.fromNodeId) { mutableSetOf() } += link
        linkIndex.getOrPut(link.toNodeId) { mutableSetOf() } += link
    }

    fun removeLink(link: Link) {
        links.remove(link)
        linkIndex[link.fromNodeId]?.remove(link)
        linkIndex[link.toNodeId]?.remove(link)
    }

    fun getLinks(): Set<Link> = links

    fun getLinks(nodeId: Int): Set<Link> = linkIndex[nodeId] ?: emptySet()

    fun addLinks(links: List<Link>) {
        links.forEach(this::addLink)
//        computeComponents()
    }

    fun removeLinks(nodeId: Int): Set<Link> {
        val removed = linkIndex.remove(nodeId) ?: emptySet<Link>()
        removed.forEach(this::removeLink)
        return removed
    }

    // ports

    fun addPort(
        nodeId: Int,
        type: PortType,
        key: String,
        output: Boolean,
        exposed: Boolean = false
    ) = Port(id, nodeId, key, type, output, exposed).also {
//        ports.getOrPut(nodeId) { mutableMapOf() }[key] = it
        getNode(nodeId).ports[key] = it
    }

    fun removePort(nodeId: Int, key: String) {
        getNode(nodeId).ports.remove(key)
    }

    fun getPort(nodeId: Int, key: String): Port {
        return getNode(nodeId).ports[key] ?: error("port not found $nodeId:$key")
    }

    fun getPorts(): List<Port> = nodes.values.fold(mutableListOf()) { a, v ->
        a.addAll(v.ports.values)
        a
    }

    // properties

    fun <T : Any> addProperty(
        nodeId: Int,
        key: Property.Key<T>,
        value: T,
        exposed: Boolean = false
    ) =
        Property(id, nodeId, key, value, exposed).also {
            getNode(nodeId).properties[key.name] = it
        }

    fun removeProperty(nodeId: Int, key: String) {
    }

    fun getProperty(nodeId: Int, key: String): Property =
        getNode(nodeId).properties[key] ?: error("property not found $nodeId:$key")

    fun <T : Any> getPropertyValue(nodeId: Int, key: Property.Key<T>): T =
        getProperty(nodeId, key.name).value as T

    fun getProperties(): Collection<Property> = nodes.values.fold(mutableListOf()) { a, n ->
        a += n.properties.values
        a
    }

    fun <T : Any> setProperty(nodeId: Int, key: Property.Key<T>, value: T) {
        getProperty(nodeId, key.name).value = value
    }

    // ----------------------------------

    fun setExposed(nodeId: Int, key: String, exposed: Boolean) {
        getPort(nodeId, key).exposed = exposed
    }

    fun setExposed(nodeId: Int, key: Property.Key<*>, exposed: Boolean) {
        getProperty(nodeId, key.name).exposed = exposed
    }

    fun getFirstNode(): Node? = nodes.values.firstOrNull()

//    fun getConnectors(nodeId: Int): List<Connector> {
//        val node = getNode(nodeId)
//        val ports = node.ports.toMutableMap()
//        val nodeLinks = getLinks(nodeId)
//
//        return (nodeLinks.map {
//            if (it.fromNodeId == nodeId) {
//                ports.remove(it.fromPortId)!!
//                val port = getPort(nodeId, it.fromPortId)
//                Connector(node, port, it)
//            } else {
//                ports.remove(it.toPortId)!!
//                val port = getPort(nodeId, it.toPortId)
//                Connector(node, port, it)
//            }
//        } + ports.map { Connector(node, it.value) }).sortedBy { it.port.key }
//    }
//
//    fun getOpenConnectors(connector: Connector): List<Connector> {
//        val node = connector.node
//        val port = connector.port
//        val connectors = mutableListOf<Connector>()
//        nodes.forEach {
//            val n = it.value
//            if (n.id != node.id) {
//                connectors += n.ports.filter {
//                    it.value.type == port.type
//                            && it.value.output != port.output
//                            && (it.value.output || !isConnected(n, it.value))
//                }.map { Connector(n, it.value) }
//            }
//        }
//        return connectors
//    }
//
//    fun getPotentialConnectors(connector: Connector): List<Connector> {
//        val port = connector.port
//        val connectors = mutableListOf<Connector>()
////        Nodes.forEach { n ->
////            connectors += n.ports.filter {
////                it.value.type == port.type
////                        && it.value.output != port.output
////            }
////                .map { Connector(n.copy(id), it.value) }
////        }
//        return connectors
//    }
//
//    fun getCreationConnectors(): List<Connector> {
//        val connectors = mutableListOf<Connector>()
////        Nodes.filter { it.producer }.forEach { n ->
////            val port = n.ports.values.first { it.output }
////            connectors += Connector(n.copy(id), port)
////        }
//        return connectors
//    }

    private fun isConnected(node: Node, port: Port): Boolean {
        return linkIndex[node.id]?.any {
            it.fromNodeId == node.id && it.fromPortId == port.key
                    || it.toNodeId == node.id && it.toPortId == port.key
        } ?: false
    }

    fun copy(
        id: String = this.id,
        creatorId: String = this.creatorId,
        name: String = this.name,
        description: String = this.description
    ): Network {
        return Network(id, creatorId, name).also { network ->
            nodes.values.forEach {
                network.addNode(it, it.id)
            }
            links.forEach { network.addLink(it) }
        }
    }

    // find connected components to detect cycles
    // Kosaraju-Sharir algorithm
    // https://algs4.cs.princeton.edu/42digraph/
    // for now just mark that a link is in a cycle
    fun computeComponents() {
        val mark = mutableSetOf<Pair<Int, String>>()
        // reverse post order traversal of reversed graph
        val result = LinkedList<Pair<Int, String>>()
        nodes.keys.forEach { node ->
            dfsReverse(node, mark, result)
        }
//        Log.d(TAG, "result " + result.joinToString())

        mark.clear()
        val components = mutableListOf<MutableList<Pair<Int, String>>>()
        result.forEach {
            val component = mutableListOf<Pair<Int, String>>()
            components.add(component)
//            Log.d(TAG, "node ${it.first} ${nodes[it.first]} ${it.second}")
            val output = getNode(it.first).ports[it.second]?.output ?: true
            if (!output && it !in mark) {
                mark.add(it)
                component.add(it)
            }
            dfs(it.first, mark, component, if (output) it.second else null)
//            Log.d(TAG, component.joinToString())
            if (component.size > 1) {
                component.add(component.removeAt(0))
                component.windowed(2, 2) {
                    val from = it[0]
                    val to = it[1]
                    val link = Link(from.first, from.second, to.first, to.second)
                    Log.d(TAG, "cycle $link")
                    linkIndex[from.first]!!.first { it == link }.inCycle = true
                }
            }
        }

//        Log.d(TAG, "links ${links.joinToString()}")
    }

    private fun dfs(
        nodeId: Int,
        marks: MutableSet<Pair<Int, String>>,
        result: MutableList<Pair<Int, String>>,
        portId: String? = null
    ) {
        val links = linkIndex[nodeId]?.filter {
            it.fromNodeId == nodeId
                    && (portId == null || it.fromPortId == portId)
        } ?: emptyList()
        links.forEach { link ->
            val key1 = Pair(link.fromNodeId, link.fromPortId)
            if (key1 !in marks) {
                marks.add(key1)
                result.add(key1)
            }
            val key2 = Pair(link.toNodeId, link.toPortId)
            if (key2 !in marks) {
                marks.add(key2)
                result.add(key2)
                dfs(link.toNodeId, marks, result)
            }
        }
    }

    private fun dfsReverse(
        nodeId: Int,
        marks: MutableSet<Pair<Int, String>>,
        result: LinkedList<Pair<Int, String>>
    ) {
        val links = linkIndex[nodeId]?.filter { it.toNodeId == nodeId } ?: emptyList()
        links.forEach { link ->
            val key1 = Pair(link.toNodeId, link.toPortId)
            if (key1 !in marks) {
                marks.add(key1)
                val key2 = Pair(link.fromNodeId, link.fromPortId)
                if (key2 !in marks) {
                    marks.add(key2)
                    dfsReverse(link.fromNodeId, marks, result)
                    result.push(key2)
                }
                result.push(key1)
            }
        }
    }

    companion object {
        const val TAG = "Network"
        const val COPY_ID_SKIP = 10_000
    }
}