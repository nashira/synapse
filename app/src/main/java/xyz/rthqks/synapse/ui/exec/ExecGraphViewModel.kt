package xyz.rthqks.synapse.ui.exec

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
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
            // right now start launches coroutines and does not join them.
            // there is a period after start during which calling stop
            // will result in hung coroutines.
            // this delay will not block any threads, it will simply keep
            // startJob active to allow for the nodes to settle
            // TODO: have start not return until it would be safe to call stop
            delay(250)
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
                Toast.makeText(context, "TIMEOUT", Toast.LENGTH_LONG).show()
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
