package com.rthqks.synapse.logic

import android.util.Log
import com.rthqks.synapse.R
import java.util.*

class Network(
    val id: Int
) {
    private val nodes = mutableMapOf<Int, Node>()
    private val links = mutableSetOf<Link>()
    private val linkIndex = mutableMapOf<Int, MutableSet<Link>>()
    val properties = Properties()
    private var maxNodeId = 0
//    private var components

    val name: String get() = properties[NetworkName]

    init {
        properties.put(
            Property(
                NetworkName,
                TextType(
                    R.string.property_name_network_name,
                    R.drawable.ic_text_fields
                ), "Network"
            ), TextConverter
        )
        properties.put(
            Property(
                CropToFit,
                ToggleType(
                    R.string.property_title_crop_to_fit, R.drawable.ic_crop,
                    R.string.property_subtitle_crop_to_fit_enabled,
                    R.string.property_subtitle_crop_to_fit_disabled
                ), value = true, requiresRestart = true
            ), BooleanConverter
        )
    }

    fun addNode(node: Node): Node {
        if (node.id == -1) {
            node.id = ++maxNodeId
        }
        nodes[node.id] = node
        maxNodeId = max(maxNodeId, node.id)
        return  node
    }

    fun nodeCount() = nodes.size

    fun removeNode(nodeId: Int): Node? {
        return nodes.remove(nodeId)
    }

    fun getFirstNode(): Node? = nodes.values.firstOrNull()

    fun getNode(nodeId: Int): Node? {
//        Log.d(TAG, "getNode $nodeId")
        return nodes[nodeId]
    }

    fun getNodes(): List<Node> {
        return nodes.values.toList()
    }

    fun getLinks(): Set<Link> = links

    fun getLinks(nodeId: Int): Set<Link> = linkIndex[nodeId] ?: emptySet()

    fun addLink(link: Link) {
        addLinkNoCompute(link)
        computeComponents()
    }

    fun addLinkNoCompute(link: Link) {
        links.add(link)
        linkIndex.getOrPut(link.fromNodeId) { mutableSetOf() } += link
        linkIndex.getOrPut(link.toNodeId) { mutableSetOf() } += link
    }

    fun removeLink(link: Link) {
        links.remove(link)
        linkIndex[link.fromNodeId]?.remove(link)
        linkIndex[link.toNodeId]?.remove(link)
    }

    fun removeLinks(nodeId: Int): Set<Link> {
        val removed = linkIndex.remove(nodeId) ?: emptySet<Link>()
        removed.forEach(this::removeLink)
        return removed
    }

    fun addLinks(links: List<Link>) {
        links.forEach(this::addLinkNoCompute)
        computeComponents()
    }

    fun getConnectors(nodeId: Int): List<Connector> {
        val node = getNode(nodeId) ?: return emptyList()
        val ports = node.getPortIds().toMutableSet()
        val nodeLinks = getLinks(nodeId)

        return (nodeLinks.map {
            if (it.fromNodeId == nodeId) {
                ports.remove(it.fromPortId)
                Connector(node, node.getPort(it.fromPortId), it)
            } else {
                ports.remove(it.toPortId)
                Connector(node, node.getPort(it.toPortId), it)
            }
        } + ports.map { Connector(node, node.getPort(it)) }).sortedBy { it.port.id }
    }

    fun getOpenConnectors(connector: Connector): List<Connector> {
        val node = connector.node
        val port = connector.port
        val connectors = mutableListOf<Connector>()
        nodes.forEach {
            val n = it.value
            if (n.id != node.id) {
                connectors += n.ports.filter {
                    it.value.type == port.type
                            && it.value.output != port.output
                            && (it.value.output || !isConnected(n, it.value))
                }.map { Connector(n, it.value) }
            }
        }
        return connectors
    }

    fun getPotentialConnectors(connector: Connector): List<Connector> {
        val port = connector.port
        val connectors = mutableListOf<Connector>()
        Nodes.forEach { n ->
            connectors += n.ports.filter {
                it.value.type == port.type
                        && it.value.output != port.output
            }
                .map { Connector(n.copy(id), it.value) }
        }
        return connectors
    }

    fun getCreationConnectors(): List<Connector> {
        val connectors = mutableListOf<Connector>()
        Nodes.filter { it.producer }.forEach { n ->
            val port = n.ports.values.first { it.output }
            connectors += Connector(n.copy(id), port)
        }
        return connectors
    }

    private fun isConnected(node: Node, port: Port): Boolean {
        return linkIndex[node.id]?.any {
            it.fromNodeId == node.id && it.fromPortId == port.id
                    || it.toNodeId == node.id && it.toPortId == port.id
        } ?: false
    }

    fun copy(): Network {
        return Network(id).also {
            it.nodes += nodes
            it.links += links
            it.properties += properties
            val ei = mutableMapOf<Int, MutableSet<Link>>()
            linkIndex.forEach { ei[it.key] = it.value.toMutableSet() }
            it.linkIndex += ei
            it.maxNodeId = COPY_ID_SKIP
        }
    }

    // find connected components to detect cycles
    // Kosaraju-Sharir algorithm
    // https://algs4.cs.princeton.edu/42digraph/
    // for now just make that a link is in a cycle
    fun computeComponents() {
        val mark = mutableSetOf<Pair<Int, String>>()
        // reverse post order traversal of reversed graph
        val result = LinkedList<Pair<Int, String>>()
        nodes.keys.forEach { node ->
            dfsReverse(node, mark, result)
        }
        Log.d(TAG, "result " + result.joinToString())

        mark.clear()
        val components = mutableListOf<MutableList<Pair<Int, String>>>()
        result.forEach {
            val component = mutableListOf<Pair<Int, String>>()
            components.add(component)
            Log.d(TAG, "node ${it.first} ${nodes[it.first]} ${it.second}")
            val output = nodes[it.first]!!.getPort(it.second).output
            if (!output && it !in mark) {
                mark.add(it)
                component.add(it)
            }
            dfs(it.first, mark, component, if (output) it.second else null)
            Log.d(TAG, component.joinToString())
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

        Log.d(TAG, "links ${links.joinToString()}")
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

    private fun max(i: Int, j: Int) = if (i > j) i else j

    companion object {
        const val TAG = "Network"
        const val COPY_ID_SKIP = 10_000
    }
}