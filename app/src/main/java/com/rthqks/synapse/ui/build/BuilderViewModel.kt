package com.rthqks.synapse.ui.build

import android.util.Log
import android.view.SurfaceView
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rthqks.synapse.data.*
import com.rthqks.synapse.exec.Executor
import com.rthqks.synapse.logic.*
import com.rthqks.synapse.util.Consumable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class BuilderViewModel @Inject constructor(
    private val executor: Executor,
    private val dao: SynapseDao
) : ViewModel() {
    private val consumable = Consumable<SwipeEvent>()
    val onSwipeEvent = MutableLiveData<Consumable<SwipeEvent>>()
    lateinit var network: Network

    val networkChannel = MutableLiveData<Network>()
    val connectionChannel = MutableLiveData<Connector>()
    val nodesChannel = MutableLiveData<AdapterState<Node>>()
    val titleChannel = MutableLiveData<Int>()
    val menuChannel = MutableLiveData<Int>()
    private var nodeAfterCancel: Node? = null

    init {
        executor.initialize(true)
    }

    fun setNetworkId(networkId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (networkId == -1) {
                val rowId = dao.insertNetwork(NetworkData(0))

                network = Network(rowId.toInt())
                Log.d(TAG, "created: $network")
            } else {
                network = dao.getFullNetwork(networkId)
                Log.d(TAG, "loaded: $network")
            }
            networkChannel.postValue(network)

            nodesChannel.postValue(AdapterState(0, listOf(PROPERTIES_NODE)))

            executor.initializeNetwork(network)
        }
    }

    fun showFirstNode() {
        val nextNode = network.getFirstNode() ?: run {
            connectionChannel.postValue(Connector(CREATION_NODE, FAKE_PORT))
            CREATION_NODE
        }
        nodesChannel.value = AdapterState(0, listOf(PROPERTIES_NODE, nextNode))
        updateStartState()
    }

    fun swipeToNode(node: Node) {
        nodesChannel.value = AdapterState(0, listOf(PROPERTIES_NODE, node))
        updateStartState()
    }

    fun swipeEvent(event: SwipeEvent) {
        consumable.item = event
        onSwipeEvent.value = consumable
    }

    fun preparePortSwipe(connector: Connector) {
        val port = connector.port
        connector.link?.let {
            val leftNode = network.getNode(it.fromNodeId)!!
            val rightNode = network.getNode(it.toNodeId)!!
            val current = if (port.output) 0 else 1
            nodesChannel.value = AdapterState(current, listOf(leftNode, rightNode))
        } ?: run {

            connectionChannel.value = connector
            if (connector.port.output) {
                nodesChannel.value = AdapterState(0, listOf(connector.node, CONNECTION_NODE))
            } else {
                nodesChannel.value = AdapterState(1, listOf(CONNECTION_NODE, connector.node))
            }
        }
    }

    fun startConnection(connector: Connector) {
        connectionChannel.value = connector
        if (connector.port.output) {
            nodesChannel.value = AdapterState(1, listOf(connector.node, CONNECTION_NODE), true)
        } else {
            nodesChannel.value = AdapterState(0, listOf(CONNECTION_NODE, connector.node), true)
        }
    }

    fun getNode(nodeId: Int): Node = network.getNode(nodeId)!!

    fun updateCurrentItem(currentItem: Int) {
        nodesChannel.value?.let {
            val item = it.items[currentItem]
            val other = it.items.firstOrNull { it != item }
            if (item.type != NodeType.Connection && item.type != NodeType.Creation) {
                nodesChannel.value = AdapterState(0, listOf(item))
            } else {
                nodesChannel.value = AdapterState(currentItem, it.items)
            }
            if (other?.type == NodeType.Connection || other?.type == NodeType.Creation) {
                restartNetwork()
            }
            updateStartState()
        }
    }

    fun cancelConnection() {
//        restartNetwork()

        nodeAfterCancel?.let {
            nodesChannel.value = AdapterState(0, listOf(it))
            nodeAfterCancel = null
            return
        }
        nodesChannel.value?.let {
            val index = it.items.indexOfFirst { it.type != NodeType.Connection }
            nodesChannel.value = AdapterState(index, it.items, true)
        }
    }

    fun completeConnection(connector: Connector) {
        Log.d(TAG, "completing connection ${connector.node.type} ${connector.node.id}")

        connectionChannel.value?.let {

            val addNode = connector.node.id <= -1
            Log.d(TAG, "completing connection ${it.node.type} ${it.node.id}")

            if (addNode) {
                // new node
                nodeAfterCancel = null
                network.addNode(connector.node)
                viewModelScope.launch(Dispatchers.IO) {
                    dao.insertNode(
                        NodeData(
                            connector.node.id,
                            network.id,
                            connector.node.type
                        )
                    )

                }
            }

            if (it.node.type != NodeType.Creation) {
                // new edge
                val from = if (connector.port.output) connector else it
                val to = if (connector.port.output) it else connector

                Log.d(
                    TAG,
                    "connecting ${from.node.type}:${from.port.id} to ${to.node.type}:${to.port.id}"
                )

                network.addLink(from.node.id, from.port.id, to.node.id, to.port.id)
                viewModelScope.launch(Dispatchers.IO) {
                    dao.insertLink(
                        LinkData(
                            network.id,
                            from.node.id,
                            from.port.id,
                            to.node.id,
                            to.port.id
                        )
                    )
                }
            }

            if (addNode) {
                restartNetwork()
                nodesChannel.value = AdapterState(0, listOf(connector.node))
            }
        }
    }

    private fun restartNetwork() {
        executor.stop()
        executor.releaseNetwork()
        executor.initializeNetwork(network)
        updateStartState()
        networkChannel.postValue(network)
    }

    fun setTitle(@StringRes resId: Int) {
        titleChannel.value = resId
    }

    fun setMenu(@MenuRes menuId: Int) {
        menuChannel.value = menuId
    }

    fun onAddNode() {
        connectionChannel.postValue(Connector(CREATION_NODE, FAKE_PORT))
        nodesChannel.value?.let {
            nodeAfterCancel = it.items[it.currentItem]
        }
        nodesChannel.postValue(AdapterState(0, listOf(CREATION_NODE)))
    }

    fun deleteLink(link: Link) {
        network.removeLink(link)
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteLink(
                network.id,
                link.fromNodeId, link.fromPortId,
                link.toNodeId, link.toPortId
            )
        }
    }

    fun deleteNode() {
        nodesChannel.value?.let {
            val node = it.items[it.currentItem]
            network.removeLinks(node.id)
            network.removeNode(node.id)

            val firstNode = network.getFirstNode()

            firstNode?.let {
                nodesChannel.value = AdapterState(0, listOf(firstNode))
            } ?: run {
                connectionChannel.value = Connector(CREATION_NODE, FAKE_PORT)
                nodesChannel.value = AdapterState(0, listOf(CREATION_NODE))
            }

            restartNetwork()

            viewModelScope.launch(Dispatchers.IO) {
                dao.deleteNode(node.networkId, node.id)
                dao.deleteLinksForNode(node.networkId, node.id)
            }
        }
    }

    fun jumpToNode(node: Node) {
        nodesChannel.value = AdapterState(0, listOf(node))
        updateStartState()
    }

    fun getConnector(nodeId: Int, portId: String): Connector {
        val node = getNode(nodeId)
        val port = node.getPort(portId)
        return Connector(node, port)
    }

    override fun onCleared() {
        Log.d(TAG, "onCleared")
        Log.d(TAG, "release")
        executor.releaseNetwork()
        executor.release()
        Log.d(TAG, "released")
        super.onCleared()
    }

    private fun updateStartState() {
        nodesChannel.value?.also {
            val current = it.items.any { it.id != NodeMap[NodeType.Properties]?.id }
            if (current) {
                executor.start()
            } else {
                executor.stop()
            }
        }
    }

    fun stopExecution() {
        executor.stop()
    }

    fun setSurfaceView(nodeId: Int, portId: String?, surfaceView: SurfaceView) {
        viewModelScope.launch {
            executor.setPreviewSurfaceView(nodeId, portId, surfaceView)
        }
    }

    fun addConnectionPreview(source: Connector, connectors: List<Connector>) {
        executor.stop()
        executor.addConnectionPreviews(source, connectors)
        executor.start()
    }

    suspend fun waitForExecutor() {
        executor.await()
    }

    fun onBackPressed(): Boolean {
        return nodesChannel.value?.let {
            when (it.items[it.currentItem].id) {
                PROPERTIES_NODE.id -> false
                CREATION_NODE.id,
                CONNECTION_NODE.id -> {
                    cancelConnection()
                    true
                }
                else -> {
                    jumpToNode(PROPERTIES_NODE)
                    true
                }
            }
        } ?: false
    }

    fun deleteNetwork() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteFullNetwork(network.id)
        }
    }

    fun onPropertyChange(nodeId: Int, property: Property<*>, properties: Properties) {
        if (property.requiresRestart) {
            restartNetwork()
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperty(
                PropertyData(
                    network.id,
                    nodeId,
                    property.key.name,
                    properties.toString(property.key)
                )
            )
        }
    }

    fun getSortedNodeList(): List<Node> {
        data class P(val node: Node, var average: Float)
        var nodes = network.getNodes().map { P(it, 0f) }

        repeat(30) {
            nodes.forEachIndexed { index, node -> node.node.position = index }

            nodes.forEach {
                val nodeId = it.node.id
                var sum = 0
                val links = network.getLinks(nodeId)
                links.forEach { link ->
                    sum += if (link.fromNodeId == nodeId) {
                        network.getNode(link.toNodeId)?.position
                    } else {
                        network.getNode(link.fromNodeId)?.position
                    } ?: error("missing node $nodeId")
                }
                it.average = sum / links.size.toFloat()
            }

            nodes = nodes.sortedBy { it.average }
        }
        nodes.forEachIndexed { index, node -> node.node.position = index }

        return nodes.map { it.node }
    }

    companion object {
        const val TAG = "BuilderViewModel"
        val PROPERTIES_NODE: Node by lazy { GetNode(NodeType.Properties) }
        val CONNECTION_NODE: Node by lazy { GetNode(NodeType.Connection) }
        val CREATION_NODE: Node by lazy { GetNode(NodeType.Creation) }
        val FAKE_PORT = Port(Port.Type.Video, "", "", false)
    }
}

data class AdapterState<T>(
    val currentItem: Int,
    val items: List<T>,
    val animate: Boolean = false
)