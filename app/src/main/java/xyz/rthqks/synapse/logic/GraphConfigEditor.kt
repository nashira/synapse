package xyz.rthqks.synapse.logic

import xyz.rthqks.synapse.data.*
import xyz.rthqks.synapse.ui.edit.PortState

class GraphConfigEditor(private val graphConfig: GraphConfig) {

    val nodes = graphConfig.nodes
    val edges = graphConfig.edges
    private val edgeMap = mutableMapOf<PortKey, EdgeConfig>()
    private var selectedPort: PortConfig? = null

    fun addNodeType(nodeType: NodeType) {
        val nodeConfig = NodeConfig(nodes.size, graphConfig.id, nodeType)
        nodeConfig.createProperties()
        nodes.add(nodeConfig)
    }

    private fun addEdge(from: PortConfig, to: PortConfig) {
        val edgeConfig =
            EdgeConfig(graphConfig.id, from.key.nodeId, from.key.key, to.key.nodeId, to.key.key)
        edges.add(edgeConfig)
        edgeMap[from.key] = edgeConfig
        edgeMap[to.key] = edgeConfig
    }

    private fun removeEdge(from: PortConfig, to: PortConfig) {
        edgeMap.remove(from.key)
        edgeMap.remove(to.key)
        edges.removeAll { it.fromKey == from.key.key && it.toKey == to.key.key }
    }

    private fun clearEdges(portConfig: PortConfig) {
        edgeMap[portConfig.key]?.let {
            edges.removeAll {
                (it.fromKey == portConfig.key.key && portConfig.key.direction == PortType.OUTPUT)
                        || (it.toKey == portConfig.key.key && portConfig.key.direction == PortType.INPUT)
            }
            edgeMap.remove(PortKey(it.fromNodeId, it.fromKey, PortType.OUTPUT))
            edgeMap.remove(PortKey(it.toNodeId, it.toKey, PortType.INPUT))
        }
    }

    fun setSelectedPort(portConfig: PortConfig?) {
        selectedPort?.let {
            when {
                it.canConnectTo(portConfig) -> {
                    clearEdges(it)
                    clearEdges(portConfig!!)
                    addEdge(it, portConfig!!)
                }
                it.isConnectedTo(portConfig) -> removeEdge(it, portConfig!!)
            }
            selectedPort = null
            return
        }
        selectedPort = portConfig
    }

    fun getPortState(portConfig: PortConfig): PortState {
        return when {
            selectedPort == portConfig && portConfig.isConnected -> PortState.SelectedConnected
            selectedPort == portConfig && !portConfig.isConnected -> PortState.SelectedUnconnected
            selectedPort == null && portConfig.isConnected -> PortState.Connected
            selectedPort == null && !portConfig.isConnected -> PortState.Unconnected
            portConfig.isConnectedTo(selectedPort) -> PortState.EligibleToDisconnect
            selectedPort!!.type::class == portConfig.type::class
                    && selectedPort?.key?.direction != portConfig.key.direction
                    && selectedPort?.key?.nodeId != portConfig.key.nodeId -> PortState.EligibleToConnect
            else -> PortState.Unconnected
        }
    }

    fun getOpenInputsForType(portConfig: PortConfig): List<PortConfig> {
        val ports = mutableListOf<PortConfig>()
        nodes.forEach { node ->
            ports += node.inputs.filter { input ->
                !edgeMap.containsKey(input.key) && input.type::class == portConfig::class
            }

        }
        return ports
    }

    fun getOpenOutputsForType(portConfig: PortConfig): List<PortConfig> {
        val ports = mutableListOf<PortConfig>()
        nodes.forEach { node ->
            ports += node.outputs.filter { output ->
                !edgeMap.containsKey(output.key) &&
                        output.type::class == portConfig::class
            }

        }
        return ports
    }

    private val PortConfig.isConnected: Boolean
        get() = edgeMap.containsKey(key)

    private fun PortConfig.isConnectedTo(portConfig: PortConfig?): Boolean {
        val other = portConfig ?: return false

        return edgeMap[key]?.let {
            (it.fromKey == key.key && it.toKey == other.key.key) ||
                    (it.fromKey == other.key.key && it.toKey == key.key)
        } ?: false
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
            properties.add(PropertyConfig(it.key, id, it.value.default.toString()))
        }
    }

    fun getNode(nodeId: Int): NodeConfig = nodes[nodeId]
}