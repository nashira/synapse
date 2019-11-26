package xyz.rthqks.synapse.ui.build

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.data.*
import xyz.rthqks.synapse.logic.GraphConfigEditor
import xyz.rthqks.synapse.ui.edit.PortState
import xyz.rthqks.synapse.util.Consumable
import javax.inject.Inject

class BuilderViewModel @Inject constructor(
    private val dao: SynapseDao
) : ViewModel() {
    private val consumable = Consumable<SwipeEvent>()
    val onSwipeEvent = MutableLiveData<Consumable<SwipeEvent>>()
    lateinit var graph: GraphData
    lateinit var graphConfigEditor: GraphConfigEditor

    val graphChannel = MutableLiveData<GraphConfigEditor>()
    val nodesChannel = MutableLiveData<AdapterState<NodeData>>()

    fun setGraphId(graphId: Int) {
        if (graphId == -1) {
            graph = GraphData(0, "Graph")
            graphConfigEditor = GraphConfigEditor(graph)

            Log.d(TAG, dao.toString())

            viewModelScope.launch(Dispatchers.IO) {
                val rowId = dao.insertGraph(graph)
                graph.id = rowId.toInt()
                Log.d(TAG, "created: $graph")
                graphChannel.postValue(graphConfigEditor)
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                graph = dao.getFullGraph(graphId)
                Log.d(TAG, "loaded: $graph")

                graphConfigEditor = GraphConfigEditor(graph)
                graphChannel.postValue(graphConfigEditor)
                nodesChannel.postValue(
                    AdapterState(0, listOf(graphConfigEditor.nodes[0]!!))
                )
            }
        }
    }

    fun swipeEvent(event: SwipeEvent) {
        consumable.item = event
        onSwipeEvent.value = consumable
    }

    fun preparePortSwipe(portConfig: PortConfig) {
        val state = graphConfigEditor.getPortState(portConfig)

        when (state) {
            PortState.Connected -> {
                val edge = graphConfigEditor.findEdges(portConfig).first()

                val leftNode = graphConfigEditor.getNode(edge.fromNodeId)
                val rightNode = graphConfigEditor.getNode(edge.toNodeId)
                val current = if (portConfig.key.direction == PortType.INPUT) 1 else 0
                nodesChannel.value = AdapterState(current, listOf(leftNode, rightNode))
            }
            PortState.Unconnected -> {
                nodesChannel.value?.let {
                    if (portConfig.key.direction == PortType.INPUT && it.currentItem == 1
                        || portConfig.key.direction == PortType.OUTPUT && it.currentItem == 0) {
                        nodesChannel.value = AdapterState(0, listOf(it.items[it.currentItem]))
                    }
                }
            }
        }
    }

    fun getNode(nodeId: Int): NodeData = graphConfigEditor.getNode(nodeId)
    fun getPortState(portConfig: PortConfig) = graphConfigEditor.getPortState(portConfig)

    fun onViewPagerIdle(viewPager: ViewPager2) {
        nodesChannel.value?.let {
            nodesChannel.value = AdapterState(viewPager.currentItem, it.items)
        }
    }

    companion object {
        const val TAG = "BuilderViewModel"
    }
}

data class AdapterState<T>(
    val currentItem: Int,
    val items: List<T>
)