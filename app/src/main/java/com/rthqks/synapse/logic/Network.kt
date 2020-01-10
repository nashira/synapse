package com.rthqks.synapse.logic

import com.rthqks.synapse.R


class Network(
    val id: Int,
    var name: String
) {
    private val nodes = mutableMapOf<Int, Node>()
    private val edges = mutableSetOf<Link>()
    private val edgeIndex = mutableMapOf<Int, MutableSet<Link>>()
    val properties = Properties()
    private var maxNodeId = 0

    init {
        properties.put(
            CropToFit,
            Property(
                CropToFit,
                ToggleType(
                    R.string.property_title_crop_to_fit, R.drawable.ic_crop,
                    R.string.property_subtitle_crop_to_fit_enabled,
                    R.string.property_subtitle_crop_to_fit_disabled
                ), true
            ), BooleanConverter
        )
    }

    fun addNode(node: Node) {
        if (node.id == -1) {
            node.id = ++maxNodeId
        }
        nodes[node.id] = node
        maxNodeId = max(maxNodeId, node.id)
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

    fun getEdges(): Set<Link> = edges

    fun getEdges(nodeId: Int): Set<Link> = edgeIndex[nodeId] ?: emptySet()

    fun addLink(
        fromNodeId: Int,
        fromKey: String,
        toNodeId: Int,
        toKey: String
    ): Link {
        val edge = Link(fromNodeId, fromKey, toNodeId, toKey)
        edges.add(edge)
        edgeIndex.getOrPut(fromNodeId) { mutableSetOf() } += edge
        edgeIndex.getOrPut(toNodeId) { mutableSetOf() } += edge
        return edge
    }

    fun removeEdge(link: Link) {
        edges.remove(link)
        edgeIndex[link.fromNodeId]?.remove(link)
        edgeIndex[link.toNodeId]?.remove(link)
    }

    fun removeEdges(nodeId: Int): Set<Link> {
        val removed = edgeIndex.remove(nodeId) ?: emptySet<Link>()
        removed.forEach(this::removeEdge)
        return removed
    }

    fun getConnectors(nodeId: Int): List<Connector> {
        val node = getNode(nodeId) ?: return emptyList()
        val ports = node.getPortIds().toMutableSet()
        val nodeEdges = getEdges(nodeId)

        return (nodeEdges.map {
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
                }
                    .map { Connector(n, it.value) }
            }
        }
        return connectors
    }

    fun getPotentialConnectors(connector: Connector): List<Connector> {
        val port = connector.port
        val connectors = mutableListOf<Connector>()
        Nodes.filter { it.type != Node.Type.OverlayFilter }.forEach { n ->
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
        Nodes.forEach { n ->
            connectors += n.ports.filter { it.value.output }
                .map { Connector(n.copy(id), it.value) }
        }
        return connectors
    }

    private fun isConnected(node: Node, port: Port): Boolean {
        return edgeIndex[node.id]?.any {
            it.fromNodeId == node.id && it.fromPortId == port.id
                    || it.toNodeId == node.id && it.toPortId == port.id
        } ?: false
    }

    fun copy(): Network {
        return Network(id, name).also {
            it.nodes += nodes
            it.edges += edges
            it.properties += properties
            val ei = mutableMapOf<Int, MutableSet<Link>>()
            edgeIndex.forEach { ei[it.key] = it.value.toMutableSet() }
            it.edgeIndex += ei
            it.maxNodeId = maxNodeId + COPY_ID_SKIP
        }
    }

    private fun max(i: Int, j: Int) = if (i > j) i else j

    companion object {
        const val TAG = "Network"
        const val COPY_ID_SKIP = 10_000
    }
}