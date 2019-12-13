package xyz.rthqks.synapse.exec

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.logic.Graph
import java.util.concurrent.Executors
import javax.inject.Inject

class Executor @Inject constructor(
    context: Context
) {
    private var graphExecutor: GraphExecutor? = null

    private var initJob: Job? = null
    private var startJob: Job? = null
    private var stopJob: Job? = null

    private val dispatcher = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    private val glesManager = GlesManager()
    private val cameraManager = CameraManager(context)
    private val assetManager = AssetManager(context)


    fun initialize() {
        Log.d(TAG, "initialize")
        glesManager.initialize()
        cameraManager.initialize()
    }

    fun release() {
        Log.d(TAG, "release")
        cameraManager.release()
        glesManager.release()
        dispatcher.close()
    }

    suspend fun start() {
        Log.d(TAG, "start")
    }

    suspend fun stop() {
        Log.d(TAG, "stop")

    }

    suspend fun initializeGraph(graph: Graph) {
        Log.d(TAG, "initialize graph ${graph.id}")

    }

    suspend fun releaseGraph(graph: Graph) {
        Log.d(TAG, "release graph ${graph.id}")

    }

    companion object {
        const val TAG = "Executor"
    }
}