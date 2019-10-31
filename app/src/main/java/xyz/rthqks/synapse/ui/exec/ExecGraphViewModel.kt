package xyz.rthqks.synapse.ui.exec

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import xyz.rthqks.synapse.core.Graph
import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.SynapseDao
import javax.inject.Inject

class ExecGraphViewModel @Inject constructor(
    private val context: Context,
    private val dao: SynapseDao
) : ViewModel() {
    private lateinit var surfaceView: SurfaceView
    val graphLoaded = MutableLiveData<GraphData>()
    private lateinit var graph: Graph
    private var initJob: Job? = null
    private var startJob: Job? = null
    private var stopJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun loadGraph(graphId: Int) {
        initJob = scope.launch {
            val graphConfig = dao.getFullGraph(graphId)
            graphLoaded.postValue(graphConfig)
            Log.d(TAG, "loaded graph: $graphConfig")
            graph = Graph(context, graphConfig)

            graph.tmp_SetSurfaceView(surfaceView)

            Log.d(TAG, "initialize")
            graph.initialize()
            Log.d(TAG, "initialized")
        }
    }

    fun startExecution() {
        startJob = scope.launch {
            initJob?.join()
            stopJob?.join()
            Log.d(TAG, "starting")
            graph.start()
            Log.d(TAG, "done starting")
        }
    }

    fun stopExecution() {
        stopJob = scope.launch {
            Log.d(TAG, "stopping")
            startJob?.join()
            graph.stop()
            Log.d(TAG, "done stopping")
        }
    }

    override fun onCleared() {
        scope.launch {
            Log.d(TAG, "release")
            withTimeoutOrNull(1000) {
                stopJob?.join()
            } ?: run {
                Log.w(TAG, "timeout waiting for stop")
            }
            graph.release()
            scope.cancel()
            Log.d(TAG, "released")
        }
        super.onCleared()
    }

    fun setSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
    }

    companion object {
        private val TAG = ExecGraphViewModel::class.java.simpleName
    }
}
