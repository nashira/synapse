package xyz.rthqks.synapse.logic


class Graph(
    val id: Int,
    val name: String
) {
    private val nodes = mutableMapOf<Int, Node>()
    private val edges = mutableSetOf<Edge>()
    private val edgeIndex = mutableMapOf<Int, MutableSet<Edge>>()
    private var nextNodeId = -1

    fun addNode(node: Node) {
        if (node.id == -1) {
            node.id = ++nextNodeId
        }
        nextNodeId = max(nextNodeId, node.id)
        nodes[node.id] = node
    }

    fun nodeCount() = nodes.size

    fun removeNode(nodeId: Int): Node? {
        return nodes.remove(nodeId)
    }

    fun getFirstNode(): Node? = nodes.values.firstOrNull()

    fun getNode(nodeId: Int): Node {
        return nodes[nodeId]!!
    }

    fun getNodes(): List<Node> {
        return nodes.values.toList()
    }

    fun getEdges(): Set<Edge> = edges

    fun getEdges(nodeId: Int): Set<Edge> = edgeIndex[nodeId] ?: emptySet()

    fun addEdge(
        fromNodeId: Int,
        fromKey: String,
        toNodeId: Int,
        toKey: String
    ) {
        val edge = Edge(fromNodeId, fromKey, toNodeId, toKey)
        edges.add(edge)
        edgeIndex.getOrPut(fromNodeId) { mutableSetOf() } += edge
        edgeIndex.getOrPut(toNodeId) { mutableSetOf() } += edge
    }

    fun removeEdge(edge: Edge) {
        edges.remove(edge)
        edgeIndex[edge.fromNodeId]?.remove(edge)
        edgeIndex[edge.toNodeId]?.remove(edge)
    }

    fun removeEdges(nodeId: Int): Set<Edge> {
        val removed = edgeIndex.remove(nodeId) ?: emptySet<Edge>()
        removed.forEach(this::removeEdge)
        return removed
    }

    fun getConnectors(nodeId: Int): List<Connector> {
        val node = getNode(nodeId)
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
                connectors += n.ports.filter { it.value.type == port.type
                        && it.value.output != port.output
                        && (it.value.output || !isConnected(n, it.value)) }
                    .map { Connector(n, it.value) }
            }
        }
        return connectors
    }

    fun getPotentialConnectors(connector: Connector): List<Connector> {
        val port = connector.port
        val connectors = mutableListOf<Connector>()
        Node.All.forEach { n ->
            connectors += n.ports.filter { it.value.type == port.type
                    && it.value.output != port.output }
                .map { Connector(n.copy(id), it.value) }
        }
        return connectors
    }

    fun getCreationConnectors(): List<Connector> {
        val connectors = mutableListOf<Connector>()
        Node.All.forEach { n ->
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

    private fun max(i: Int, j: Int) = if (i > j) i else j

    companion object {
    }
}