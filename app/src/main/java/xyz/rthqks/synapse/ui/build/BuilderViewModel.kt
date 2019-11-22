package xyz.rthqks.synapse.ui.build

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.NodeConfig
import xyz.rthqks.synapse.data.PortConfig
import xyz.rthqks.synapse.data.SynapseDao
import xyz.rthqks.synapse.logic.GraphConfigEditor
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
            }
        }
    }

    fun swipeEvent(event: SwipeEvent) {
        consumable.item = event
        onSwipeEvent.value = consumable
    }

    fun getNode(nodeId: Int): NodeConfig = graphConfigEditor.getNode(nodeId)
    fun getPortState(portConfig: PortConfig) = graphConfigEditor.getPortState(portConfig)

    companion object {
        const val TAG = "BuilderViewModel"
    }
}
