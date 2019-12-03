package xyz.rthqks.synapse.logic

class Graph(
    val id: Int,
    val name: String
) {
    private val nodes = mutableMapOf<Int, Node>()
    private val edges = mutableListOf<Edge>()
    private var nextNodeId = 0

    fun addNode(node: Node) {
        nodes[node.id] = node
    }

//    fun addNode(type: Node.Type): Node {
//        val node = type.node(id, nextNodeId++)
//        nodes[node.id] = node
//        return node
//    }

    fun removeNode(nodeId: Int) {
        nodes.remove(nodeId)
    }

    fun getNode(nodeId: Int): Node {
        return nodes[nodeId]!!
    }

    fun addEdge(
        fromNodeId: Int,
        fromKey: String,
        toNodeId: Int,
        toKey: String
    ) {

        val fromNode = getNode(fromNodeId)
        val toNode = getNode(toNodeId)

        val fromPort = fromNode.getPort(fromKey)
        val toPort = fromNode.getPort(toKey)

        edges.add(Edge(fromNode, fromPort, toNode, toPort))
    }

    fun removeEdge(edge: Edge) {
        edges.remove(edge)
    }

    fun getConnectors(nodeId: Int): List<Connector> {
        val node = getNode(nodeId)
        val ports = node.getPortIds().toMutableSet()
        val nodeEdges = edges.filter { it.fromNode.id == nodeId || it.toNode.id == nodeId }

        return nodeEdges.map {
            if (it.fromNode.id == nodeId) {
                ports.remove(it.fromPort.id)
                Connector(node, it.fromPort, it)
            } else {
                ports.remove(it.toPort.id)
                Connector(node, it.toPort, it)
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
                        && !isConnected(n, it.value) }
                    .map { Connector(n, it.value) }
            }
        }
        return connectors
    }

    fun getPotentialConnectors(connector: Connector): List<Connector> {
        val port = connector.port
        val connectors = mutableListOf<Connector>()
        Node.Type.values().forEach {
            val n = NodeMap[it] ?: error("missing node map entry")
            connectors += n.ports.filter { it.value.type == port.type
                    && it.value.output != port.output }
                .map { Connector(n.copy(id), it.value) }
        }
        return connectors
    }

    private fun isConnected(node: Node, port: Port): Boolean {
        return edges.any {
            it.fromNode.id == node.id && it.fromPort.id == port.id
                    || it.toNode.id == node.id && it.toPort.id == port.id
        }
    }

    companion object {
    }
}