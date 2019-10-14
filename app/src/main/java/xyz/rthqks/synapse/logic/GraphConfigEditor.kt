package xyz.rthqks.synapse.logic

import xyz.rthqks.synapse.data.*
import xyz.rthqks.synapse.ui.edit.PortState

class GraphConfigEditor(val graphConfig: GraphConfig) {

    val nodes = graphConfig.nodes
    val edges = graphConfig.edges.toMutableSet()
    private var selectedPort: PortConfig? = null

    fun addNodeType(nodeType: NodeType): NodeConfig {
        val nodeConfig = NodeConfig(nodes.size, graphConfig.id, nodeType)
        nodeConfig.createProperties()
        nodes.add(nodeConfig)
        return nodeConfig
    }

    private fun addEdge(from: PortConfig, to: PortConfig): EdgeConfig {
        val edge = from.edgeTo(to)
        edges.add(edge)
        return edge
    }

    private fun removeEdge(from: PortConfig, to: PortConfig): EdgeConfig {
        val edge = from.edgeTo(to)
        edges.remove(edge)
        return edge
    }

    private fun clearEdges(portConfig: PortConfig): List<EdgeConfig> {
        val toRemove = edges.filter {
            (it.from == portConfig.key) || (it.to == portConfig.key)
        }
        edges.removeAll(toRemove)
        return toRemove
    }

    fun setSelectedPort(portConfig: PortConfig?): Pair<EdgeConfig?, List<EdgeConfig>> {
        selectedPort?.let {
            val removed = mutableListOf<EdgeConfig>()
            var added: EdgeConfig? = null
            when {
                it.canConnectTo(portConfig) -> {
                    removed.addAll(clearEdges(it))
                    removed.addAll(clearEdges(portConfig!!))
                    added = if (it.key.direction == PortType.OUTPUT) {
                        addEdge(it, portConfig!!)
                    } else {
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


    fun getNode(nodeId: Int): NodeConfig = nodes[nodeId]

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

    private fun NodeConfig.createProperties() {
        type.properties.forEach {
            properties.add(PropertyConfig(graphId, id, it.key, it.value.default.toString()))
        }
    }

    private fun PortConfig.edgeTo(other: PortConfig): EdgeConfig =
        EdgeConfig(graphConfig.id, key.nodeId, key.key, other.key.nodeId, other.key.key)


    private val EdgeConfig.from get() = PortKey(fromNodeId, fromKey, PortType.OUTPUT)

    private val EdgeConfig.to get() = PortKey(toNodeId, toKey, PortType.INPUT)
}