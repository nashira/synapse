package xyz.rthqks.synapse.ui.edit

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import xyz.rthqks.synapse.data.*
import xyz.rthqks.synapse.logic.GraphConfigEditor
import javax.inject.Inject

class EditGraphViewModel @Inject constructor() : ViewModel() {

    val onAddNodeClicked = MutableLiveData<Unit>()
    val graph = GraphConfig(0, "Test")
    val graphConfigEditor = GraphConfigEditor(graph)
    val graphChannel = MutableLiveData<GraphConfigEditor>()
    val onNodeAdded = MutableLiveData<Unit>()
    val onPortSelected = MutableLiveData<Unit>()

    init {
        graphChannel.value = graphConfigEditor
    }

    fun stop() {

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

    fun getOpenOutputsForType(dataType: DataType) = graphConfigEditor.getOpenOutputsForType(dataType)

    fun getOpenInputsForType(dataType: DataType) = graphConfigEditor.getOpenInputsForType(dataType)

    fun getNode(nodeId: Int): NodeConfig = graphConfigEditor.getNode(nodeId)

    companion object {
        private val TAG = EditGraphViewModel::class.java.simpleName
    }
}
