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
    val graph = GraphConfig(0, "Test")
    val graphConfigEditor = GraphConfigEditor(graph)
    val graphChannel = MutableLiveData<GraphConfigEditor>()
    val onNodeAdded = MutableLiveData<Unit>()
    val onPortSelected = MutableLiveData<Unit>()

    init {
        graphChannel.value = graphConfigEditor
        Log.d(TAG, dao.toString())

        viewModelScope.launch(Dispatchers.Default) {
//            val graph = dao.getGraphs()
            Log.d(TAG, graph.toString())
            dao.saveGraph(graph)
        }
    }

    fun addNodeType(nodeType: NodeType) {
        graphConfigEditor.addNodeType(nodeType)
        onNodeAdded.value = Unit
    }

    fun getPortState(portConfig: PortConfig) = graphConfigEditor.getPortState(portConfig)

    fun setSelectedPort(portConfig: PortConfig) {
        graphConfigEditor.setSelectedPort(portConfig)
        onPortSelected.value = Unit
    }

    fun getOpenOutputsForType(portConfig: PortConfig) = graphConfigEditor.getOpenOutputsForType(portConfig)

    fun getOpenInputsForType(portConfig: PortConfig) = graphConfigEditor.getOpenInputsForType(portConfig)

    fun getNode(nodeId: Int): NodeConfig = graphConfigEditor.getNode(nodeId)

    companion object {
        private val TAG = EditGraphViewModel::class.java.simpleName
    }
}
