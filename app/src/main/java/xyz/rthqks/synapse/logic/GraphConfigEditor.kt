package xyz.rthqks.synapse.logic

import xyz.rthqks.synapse.data.*
import xyz.rthqks.synapse.ui.edit.PortState

class GraphConfigEditor(val graphData: GraphData) {

    val nodes = mutableMapOf<Int, NodeData>()
    val edges = graphData.edges.toMutableSet()
    private var selectedPort: PortConfig? = null

    init {
        graphData.nodes.forEach { nodes[it.id] = it }
    }

    fun addNodeType(nodeType: NodeType): NodeData {
        val nodeConfig = NodeData(nodes.size, graphData.id, nodeType)
        nodeConfig.createProperties()
        nodes[nodeConfig.id] = nodeConfig
        return nodeConfig
    }

    private fun addEdge(from: PortConfig, to: PortConfig): EdgeData {
        val edge = from.edgeTo(to)
        edges.add(edge)
        return edge
    }

    private fun removeEdge(from: PortConfig, to: PortConfig): EdgeData {
        val edge = from.edgeTo(to)
        edges.remove(edge)
        return edge
    }

    fun clearEdges(portConfig: PortConfig): List<EdgeData> {
        val toRemove = edges.filter {
            (it.from == portConfig.key) || (it.to == portConfig.key)
        }
        edges.removeAll(toRemove)
        return toRemove
    }

    fun findEdges(portConfig: PortConfig): List<EdgeData> {
        return edges.filter {
            (it.from == portConfig.key) || (it.to == portConfig.key)
        }
    }

    fun setSelectedPort(portConfig: PortConfig?): Pair<EdgeData?, List<EdgeData>> {
        selectedPort?.let {
            val removed = mutableListOf<EdgeData>()
            var added: EdgeData? = null
            when {
                it.canConnectTo(portConfig) -> {
                    added = if (it.key.direction == PortType.OUTPUT) {
                        removed.addAll(clearEdges(portConfig!!))
                        addEdge(it, portConfig!!)
                    } else {
                        removed.addAll(clearEdges(it))
                        addEdge(portConfig!!, it)
                    }
                }
                it.isConnectedTo(portConfig) -> {
                    removed.add(
                        if (it.key.direction == PortType.OUTPUT) {
                            removeEdge(it, portConfig!!)
                        } else {
                            removeEdge(portConfig!!, it)
                        }
                    )
                }
            }
            selectedPort = null
            return Pair(added, removed)
        }
        selectedPort = portConfig
        return Pair(null, emptyList())
    }

    fun getPortState(portConfig: PortConfig): PortState {
        return when {
            selectedPort == portConfig && portConfig.isConnected -> PortState.SelectedConnected
            selectedPort == portConfig && !portConfig.isConnected -> PortState.SelectedUnconnected
            selectedPort == null && portConfig.isConnected -> PortState.Connected
            selectedPort == null && !portConfig.isConnected -> PortState.Unconnected
            portConfig.isConnectedTo(selectedPort) -> PortState.EligibleToDisconnect
            selectedPort!!.canConnectTo(portConfig) -> PortState.EligibleToConnect
            else -> PortState.Unconnected
        }
    }


    fun getNode(nodeId: Int): NodeData = nodes[nodeId]!!

    private val PortConfig.isConnected: Boolean
        get() = edges.any {
            it.from == key || it.to == key
        }

    private fun PortConfig.isConnectedTo(portConfig: PortConfig?): Boolean {
        val other = portConfig ?: return false

        return (key.direction == PortType.OUTPUT &&
                other.key.direction == PortType.INPUT && edges.contains(edgeTo(other)))
                || (key.direction == PortType.INPUT &&
                other.key.direction == PortType.OUTPUT &&
                edges.contains(other.edgeTo(this)))
    }

    private fun PortConfig.canConnectTo(portConfig: PortConfig?): Boolean {
        val other = portConfig ?: return false
        return key.nodeId != other.key.nodeId
                && type::class == other.type::class
                && key.direction != other.key.direction
                && !isConnectedTo(other)
    }

    private fun NodeData.createProperties() {
        type.properties.values.forEach {
            properties[it.key] = PropertyData(graphId, id, it.key.name, PropertyType.toString(it.key, it.default))
        }
    }

    private fun PortConfig.edgeTo(other: PortConfig): EdgeData =
        EdgeData(graphData.id, key.nodeId, key.key, other.key.nodeId, other.key.key)

    fun removeNode(id: Int) {
        nodes.remove(id)
    }


    private val EdgeData.from get() = PortKey(fromNodeId, fromKey, PortType.OUTPUT)

    private val EdgeData.to get() = PortKey(toNodeId, toKey, PortType.INPUT)
}