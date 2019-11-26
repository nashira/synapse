package xyz.rthqks.synapse.ui.edit

import android.net.Uri
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import xyz.rthqks.synapse.data.*
import xyz.rthqks.synapse.logic.GraphConfigEditor
import xyz.rthqks.synapse.util.Consumable
import javax.inject.Inject

class EditGraphViewModel @Inject constructor(
    private val dao: SynapseDao
) : ViewModel() {

    val onAddNodeClicked = MutableLiveData<Unit>()
    lateinit var graph: GraphData
    lateinit var graphConfigEditor: GraphConfigEditor
    val graphChannel = MutableLiveData<GraphConfigEditor>()
    val onNodeAdded = MutableLiveData<NodeData>()
    val onPortSelected = MutableLiveData<Unit>()
    val onSelectFile = MutableLiveData<Consumable<PropertyData>>()

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

    fun addNodeType(nodeType: NodeType) {
        val node = graphConfigEditor.addNodeType(nodeType)
        onNodeAdded.value = node
        onAddNodeClicked.value = null
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperties(node.properties.values)
            dao.insertNode(node)
        }
    }

    fun getPortState(portConfig: PortConfig) = graphConfigEditor.getPortState(portConfig)

    fun setSelectedPort(portConfig: PortConfig) {
        val (added, removed) = graphConfigEditor.setSelectedPort(portConfig)
        Log.d(TAG, "added: $added")
        Log.d(TAG, "removed: $removed")
        onPortSelected.value = Unit

        viewModelScope.launch(Dispatchers.IO) {
            added?.let {
                dao.insertEdge(it)
            }
            if (removed.isNotEmpty()) {
                dao.deleteEdges(removed)
            }
        }
    }

    fun getNode(nodeId: Int): NodeData = graphConfigEditor.getNode(nodeId)

    fun setGraphName(name: String) {
        graph.name = name
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertGraph(graph)
            Log.d(TAG, "saved: $graph")
        }
    }

    fun saveProperty(property: PropertyData) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProperty(property)
            Log.d(TAG, "saved: $property")
        }
    }

    fun deleteGraph() {
        runBlocking(Dispatchers.IO) {
            dao.deleteFullGraph(graph)
        }
    }

    fun removeNode(node: NodeData) {
        val edges = mutableListOf<EdgeData>()
        node.inputs.forEach {
            edges += graphConfigEditor.clearEdges(it)
        }
        node.outputs.forEach {
            edges += graphConfigEditor.clearEdges(it)
        }
        graphConfigEditor.removeNode(node.id)
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteNode(node)
            dao.deleteEdges(edges)
        }
    }

    fun selectFileFor(data: PropertyData) {
        onSelectFile.value = Consumable(data)
    }

    fun onFileSelected(data: Uri?, config: PropertyData) {
        data?.let {
            config.value = it.toString()
            saveProperty(config)
        }
    }

    companion object {
        private val TAG = EditGraphViewModel::class.java.simpleName
    }
}
