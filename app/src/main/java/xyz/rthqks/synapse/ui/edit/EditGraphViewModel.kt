package xyz.rthqks.synapse.ui.edit

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.data.*
import xyz.rthqks.synapse.logic.GraphConfigEditor
import javax.inject.Inject

class EditGraphViewModel @Inject constructor(
    private val dao: SynapseDao
) : ViewModel() {

    val onAddNodeClicked = MutableLiveData<Unit>()
    lateinit var graph: GraphConfig
    lateinit var graphConfigEditor: GraphConfigEditor
    val graphChannel = MutableLiveData<GraphConfigEditor>()
    val onNodeAdded = MutableLiveData<Unit>()
    val onPortSelected = MutableLiveData<Unit>()

    fun setGraphId(graphId: Int) {
        if (graphId == -1) {
            graph = GraphConfig(0, "Graph")
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
                graph = dao.getGraph(graphId)
                graphConfigEditor = GraphConfigEditor(graph)

                Log.d(TAG, "loaded: $graph")
                graphChannel.postValue(graphConfigEditor)
            }
        }
    }

    fun addNodeType(nodeType: NodeType) {
        val node = graphConfigEditor.addNodeType(nodeType)
        onNodeAdded.value = Unit
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperties(node.properties)
            dao.insertNode(node)
        }
    }

    fun getPortState(portConfig: PortConfig) = graphConfigEditor.getPortState(portConfig)

    fun setSelectedPort(portConfig: PortConfig) {
        graphConfigEditor.setSelectedPort(portConfig)
        onPortSelected.value = Unit
    }

    fun getOpenOutputsForType(portConfig: PortConfig) = graphConfigEditor.getOpenOutputsForType(portConfig)

    fun getOpenInputsForType(portConfig: PortConfig) = graphConfigEditor.getOpenInputsForType(portConfig)

    fun getNode(nodeId: Int): NodeConfig = graphConfigEditor.getNode(nodeId)
    fun setGraphName(name: String) {
        graph.name = name
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertGraph(graph)
            Log.d(TAG, "saved: $graph")
        }
    }

    fun saveProperty(property: PropertyConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperty(property)
            Log.d(TAG, "saved: $graph")
        }
    }

    companion object {
        private val TAG = EditGraphViewModel::class.java.simpleName
    }
}
