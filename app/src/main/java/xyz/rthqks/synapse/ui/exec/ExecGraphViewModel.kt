package xyz.rthqks.synapse.ui.exec

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.rthqks.synapse.core.Graph
import xyz.rthqks.synapse.data.GraphConfig
import xyz.rthqks.synapse.data.SynapseDao
import javax.inject.Inject

class ExecGraphViewModel @Inject constructor(
    private val context: Context,
    private val dao: SynapseDao
) : ViewModel() {
    val graphLoaded = MutableLiveData<GraphConfig>()
    private lateinit var graph: Graph

    fun loadGraph(graphId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val graphConfig = dao.getFullGraph(graphId)
            graphLoaded.postValue(graphConfig)
            Log.d(TAG, "loaded graph: $graphConfig")
            graph = Graph(context, graphConfig)
            graph.initialize()
            graph.start()
            delay(15000)
            graph.stop()
        }
    }

    companion object {
        private val TAG = ExecGraphViewModel::class.java.simpleName
    }
}
