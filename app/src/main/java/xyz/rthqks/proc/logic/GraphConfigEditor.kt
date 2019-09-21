package xyz.rthqks.proc.logic

import xyz.rthqks.proc.data.*
import xyz.rthqks.proc.ui.edit.PortState

class GraphConfigEditor(private val graphConfig: GraphConfig) {

    val nodes = graphConfig.nodes
    val edges = graphConfig.edges
    private var nextPortId: Int = 0
    private val edgeMap = mutableMapOf<Int, EdgeConfig>()
    private var selectedPort: PortConfig? = null

    fun addNodeType(nodeType: NodeType) {
        val nodeConfig = NodeConfig(nodes.size, graphConfig.id, nodeType)
        nodeConfig.createPorts()
        nodes.add(nodeConfig)
    }

    private fun addEdge(from: PortConfig, to: PortConfig) {
        val edgeConfig = EdgeConfig(from.nodeId, from.id, to.nodeId, to.id)
        edges.add(edgeConfig)
        edgeMap[from.id] = edgeConfig
        edgeMap[to.id] = edgeConfig
    }

    private fun removeEdge(from: PortConfig, to: PortConfig) {
        edgeMap.remove(from.id)
        edgeMap.remove(to.id)
        edges.removeAll { it.fromId == from.id && it.toId == to.id }
    }

    private fun clearEdges(portConfig: PortConfig) {
        edgeMap[portConfig.id]?.let {
            edges.removeAll { it.fromId == portConfig.id || it.toId == portConfig.id }
            edgeMap.remove(it.fromId)
            edgeMap.remove(it.toId)
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
            selectedPort?.dataType == portConfig.dataType
                    && selectedPort?.direction != portConfig.direction
                    && selectedPort?.nodeId != portConfig.nodeId -> PortState.EligibleToConnect
            else -> PortState.Unconnected
        }
    }

    fun getOpenInputsForType(dataType: DataType): List<PortConfig> {
        val ports = mutableListOf<PortConfig>()
        nodes.forEach { node ->
            ports += node.inputs.filter { input ->
                !edgeMap.containsKey(input.id) && input.dataType == dataType
            }

        }
        return ports
    }

    fun getOpenOutputsForType(dataType: DataType): List<PortConfig> {
        val ports = mutableListOf<PortConfig>()
        nodes.forEach { node ->
            ports += node.outputs.filter { output ->
                !edgeMap.containsKey(output.id) &&
                        output.dataType == dataType
            }

        }
        return ports
    }

    private val PortConfig.isConnected: Boolean
        get() = edgeMap.containsKey(id)

    private fun PortConfig.isConnectedTo(portConfig: PortConfig?): Boolean {
        val other = portConfig ?: return false

        return edgeMap[id]?.let {
            (it.fromId == id && it.toId == other.id) ||
                    (it.fromId == other.id && it.toId == id)
        } ?: false
    }

    private fun PortConfig.canConnectTo(portConfig: PortConfig?): Boolean {
        val other = portConfig ?: return false
        return nodeId != other.nodeId
                && dataType == other.dataType
                && direction != other.direction
                && !isConnectedTo(other)
    }

    private fun NodeConfig.createPorts() {
        type.inputs.forEach { dataType ->
            inputs.add(PortConfig(nextPortId++, graphId, id, PortConfig.DIRECTION_INPUT, dataType))
        }
        type.outputs.forEach { dataType ->
            outputs.add(
                PortConfig(
                    nextPortId++,
                    graphId,
                    id,
                    PortConfig.DIRECTION_OUTPUT,
                    dataType
                )
            )
        }
    }
}