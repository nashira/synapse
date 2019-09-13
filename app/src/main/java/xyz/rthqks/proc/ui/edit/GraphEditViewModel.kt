package xyz.rthqks.proc.ui.edit

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import xyz.rthqks.proc.data.GraphConfig
import xyz.rthqks.proc.data.NodeType
import xyz.rthqks.proc.logic.GraphConfigEditor
import javax.inject.Inject

class GraphEditViewModel @Inject constructor() : ViewModel() {

    private val graph = GraphConfig(0, "Test")
    private val graphConfigEditor = GraphConfigEditor(graph)
    val graphChannel = MutableLiveData<GraphConfigEditor>()


    init {
        graphChannel.value = graphConfigEditor
    }

    fun stop() {

    }

    fun addNodeType(nodeType: NodeType) {
        graphConfigEditor.addNodeType(nodeType)
//        graphChannel.value = graphConfigEditor
    }

    companion object {
        private val TAG = GraphEditViewModel::class.java.simpleName
    }
}
