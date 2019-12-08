package xyz.rthqks.synapse.ui.build

import android.util.Log
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.data.EdgeData
import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.NodeData
import xyz.rthqks.synapse.data.SynapseDao
import xyz.rthqks.synapse.logic.*
import xyz.rthqks.synapse.util.Consumable
import javax.inject.Inject

class BuilderViewModel @Inject constructor(
    private val dao: SynapseDao
) : ViewModel() {
    private val consumable = Consumable<SwipeEvent>()
    val onSwipeEvent = MutableLiveData<Consumable<SwipeEvent>>()
    lateinit var graph: Graph

    val graphChannel = MutableLiveData<Graph>()
    val connectionChannel = MutableLiveData<Connector>()
    val nodesChannel = MutableLiveData<AdapterState<Node>>()
    val titleChannel = MutableLiveData<Int>()
    val menuChannel = MutableLiveData<Int>()
    private var nodeAfterCancel: Node? = null

    fun setGraphId(graphId: Int) {
        if (graphId == -1) {
            viewModelScope.launch(Dispatchers.IO) {
                val rowId = dao.insertGraph(GraphData(0, "Graph"))

                graph = Graph(rowId.toInt(), "Graph")
                Log.d(TAG, "created: $graph")
                graphChannel.postValue(graph)
                onAddNode()
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                graph = dao.getFullGraph(graphId)
                Log.d(TAG, "loaded: $graph")

                graphChannel.postValue(graph)
                if (graph.nodeCount() == 0) {
                    onAddNode()
                } else {
                    val firstNode = graph.getFirstNode()
                    firstNode?.let {
                        nodesChannel.postValue(
                            AdapterState(0, listOf(firstNode))
                        )
                    }
                }
            }
        }
    }

    fun swipeEvent(event: SwipeEvent) {
        consumable.item = event
        onSwipeEvent.value = consumable
    }

    fun preparePortSwipe(connector: Connector) {
        val port = connector.port
        connector.edge?.let {
            val leftNode = graph.getNode(it.fromNodeId)
            val rightNode = graph.getNode(it.toNodeId)
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

    fun getNode(nodeId: Int): Node = graph.getNode(nodeId)

    fun onViewPagerIdle(viewPager: ViewPager2) {
        nodesChannel.value?.let {
            nodesChannel.value = AdapterState(viewPager.currentItem, it.items)
        }
    }

    fun cancelConnection() {
        nodeAfterCancel?.let {
            nodesChannel.value = AdapterState(0, listOf(it))
            nodeAfterCancel = null
            return
        }
        nodesChannel.value?.let {
            val index = it.items.indexOfFirst { it.type != Node.Type.Connection }
            nodesChannel.value = AdapterState(index, it.items, true)
        }
    }

    fun completeConnection(connector: Connector) {
        connectionChannel.value?.let {

            if (connector.node.id == -1) {
                // new node
                nodeAfterCancel = null
                graph.addNode(connector.node)
                viewModelScope.launch(Dispatchers.IO) {
                    dao.insertNode(
                        NodeData(
                            connector.node.id,
                            graph.id,
                            connector.node.type
                        )
                    )
                    nodesChannel.postValue(AdapterState(0, listOf(connector.node)))
                }
            }

            if (it.node.type != Node.Type.Creation) {
                // new edge
                val from = if (connector.port.output) connector else it
                val to = if (connector.port.output) it else connector

                Log.d(
                    TAG,
                    "connecting ${from.node.type}:${from.port.id} to ${to.node.type}:${to.port.id}"
                )

                graph.addEdge(from.node.id, from.port.id, to.node.id, to.port.id)
                viewModelScope.launch(Dispatchers.IO) {
                    dao.insertEdge(
                        EdgeData(
                            graph.id,
                            from.node.id,
                            from.port.id,
                            to.node.id,
                            to.port.id
                        )
                    )
                    nodesChannel.postValue(AdapterState(0, listOf(connector.node)))
                }
            }
        }
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

    fun deleteEdge(edge: Edge) {
        graph.removeEdge(edge)
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteEdge(
                graph.id,
                edge.fromNodeId, edge.fromPortId,
                edge.toNodeId, edge.toPortId
            )
        }
    }

    fun onDelete() {
        nodesChannel.value?.let {
            val node = it.items[it.currentItem]
            val edges = graph.removeNode(node.id)

            val firstNode = graph.getFirstNode()
            firstNode?.let {
                nodesChannel.value = AdapterState(0, listOf(firstNode))
            } ?: run {
                connectionChannel.value = Connector(CREATION_NODE, FAKE_PORT)
                nodesChannel.value = AdapterState(0, listOf(CREATION_NODE))
            }

            viewModelScope.launch(Dispatchers.IO) {
                dao.deleteNode(node.graphId, node.id)
                dao.deleteEdges(edges.map {
                    EdgeData(
                        node.graphId,
                        it.fromNodeId,
                        it.fromPortId,
                        it.toNodeId,
                        it.toPortId
                    )
                })
            }
        }
    }

    fun jumpToNode(node: Node) {
        nodesChannel.value = AdapterState(0, listOf(node))
    }

    fun getConnector(nodeId: Int, portId: String): Connector {
        val node = getNode(nodeId)
        val port = node.getPort(portId)
        return Connector(node, port)
    }

    companion object {
        const val TAG = "BuilderViewModel"
        val CONNECTION_NODE = Node.Type.Connection.node()
        val CREATION_NODE = Node.Type.Creation.node()
        val FAKE_PORT = Port(Port.Type.Video, "", "", false)
    }
}

data class AdapterState<T>(
    val currentItem: Int,
    val items: List<T>,
    val animate: Boolean = false
)