package xyz.rthqks.synapse.ui.build

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.SynapseDao
import xyz.rthqks.synapse.logic.Connector
import xyz.rthqks.synapse.logic.Graph
import xyz.rthqks.synapse.logic.Node
import xyz.rthqks.synapse.util.Consumable
import javax.inject.Inject

class BuilderViewModel @Inject constructor(
    private val dao: SynapseDao
) : ViewModel() {
    private val consumable = Consumable<SwipeEvent>()
    val onSwipeEvent = MutableLiveData<Consumable<SwipeEvent>>()
    lateinit var graph: Graph

    val graphChannel = MutableLiveData<Graph>()
    val connectionChannel = MutableLiveData<Unit>()
    val nodesChannel = MutableLiveData<AdapterState<Node>>()
    var connectionNodeId: Int = -1
        private set
    var connectionPortId: String = ""
        private set

    fun setGraphId(graphId: Int) {
        if (graphId == -1) {

            Log.d(TAG, dao.toString())

            viewModelScope.launch(Dispatchers.IO) {
                val rowId = dao.insertGraph(GraphData(0, "Graph"))

                graph = Graph(rowId.toInt(), "Graph")
                Log.d(TAG, "created: $graph")
                graphChannel.postValue(graph)
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                graph = dao.getFullGraph(graphId)
                Log.d(TAG, "loaded: $graph")
                Log.d(TAG, "loaded2: ${dao.getFullGraph(graphId)}")

                graphChannel.postValue(graph)
                nodesChannel.postValue(
                    AdapterState(0, listOf(graph.getNode(0)))
                )
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
            val leftNode = it.fromNode
            val rightNode = it.toNode
            val current = if (port.output) 0 else 1
            nodesChannel.value = AdapterState(current, listOf(leftNode, rightNode))
        } ?: run {
            connectionNodeId = connector.node.id
            connectionPortId = connector.port.id
            connectionChannel.value = Unit

            if (connector.port.output) {
                nodesChannel.value = AdapterState(0, listOf(connector.node, CONNECTION_NODE))
            } else {
                nodesChannel.value = AdapterState(1, listOf(CONNECTION_NODE, connector.node))
            }
        }
    }

    fun getNode(nodeId: Int): Node = graph.getNode(nodeId)

    fun onViewPagerIdle(viewPager: ViewPager2) {
        nodesChannel.value?.let {
            nodesChannel.value = AdapterState(viewPager.currentItem, it.items)
        }
    }

    companion object {
        const val TAG = "BuilderViewModel"
        val CONNECTION_NODE = Node(Node.Type.Connection)
    }
}

data class AdapterState<T>(
    val currentItem: Int,
    val items: List<T>
)