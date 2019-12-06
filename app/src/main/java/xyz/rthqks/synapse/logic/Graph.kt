package xyz.rthqks.synapse.logic


class Graph(
    val id: Int,
    val name: String
) {
    private val nodes = mutableMapOf<Int, Node>()
    private val edges = mutableListOf<Edge>()
    private var nextNodeId = -1

    fun addNode(node: Node) {
        if (node.id == -1) {
            node.id = ++nextNodeId
        }
        nextNodeId = max(nextNodeId, node.id)
        nodes[node.id] = node
    }

//    fun addNode(type: Node.Type): Node {
//        val node = type.node(id, nextNodeId++)
//        nodes[node.id] = node
//        return node
//    }

    fun nodeCount() = nodes.size

    fun removeNode(nodeId: Int): List<Edge> {
        val removed = mutableListOf<Edge>()
        edges.removeAll {
            (it.fromNodeId == nodeId || it.toNodeId == nodeId).also { r ->
                if (r) {
                    removed += it
                }
            }
        }
        nodes.remove(nodeId)
        return removed
    }

    fun getFirstNode(): Node? = nodes.values.firstOrNull()

    fun getNode(nodeId: Int): Node {
        return nodes[nodeId]!!
    }

    fun getNodes(): List<Node> {
        return nodes.values.toList()
    }

    fun getEdges(): List<Edge> = edges

    fun addEdge(
        fromNodeId: Int,
        fromKey: String,
        toNodeId: Int,
        toKey: String
    ) {

//        val fromNode = getNode(fromNodeId)
//        val toNode = getNode(toNodeId)
//
//        val fromPort = fromNode.getPort(fromKey)
//        val toPort = toNode.getPort(toKey)

        edges.add(Edge(fromNodeId, fromKey, toNodeId, toKey))
    }

    fun removeEdge(edge: Edge) {
        edges.remove(edge)
    }

    fun getConnectors(nodeId: Int): List<Connector> {
        val node = getNode(nodeId)
        val ports = node.getPortIds().toMutableSet()
        val nodeEdges = edges.filter { it.fromNodeId == nodeId || it.toNodeId == nodeId }

        return nodeEdges.map {
            if (it.fromNodeId == nodeId) {
                ports.remove(it.fromPortId)
                Connector(node, node.getPort(it.fromPortId), it)
            } else {
                ports.remove(it.toPortId)
                Connector(node, node.getPort(it.toPortId), it)
            }
        } + ports.map { Connector(node, node.getPort(it)) }
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
        return edges.any {
            it.fromNodeId == node.id && it.fromPortId == port.id
                    || it.toNodeId == node.id && it.toPortId == port.id
        }
    }

    private fun max(i: Int, j: Int) = if (i > j) i else j

    companion object {
    }
}