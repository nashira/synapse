package xyz.rthqks.synapse.core

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import kotlinx.coroutines.*
import xyz.rthqks.synapse.core.node.AudioPlayerNode
import xyz.rthqks.synapse.core.node.AudioSourceNode
import xyz.rthqks.synapse.core.node.CameraNode
import xyz.rthqks.synapse.core.node.SurfaceViewNode
import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.Key
import xyz.rthqks.synapse.data.NodeType

class Graph(
    private val context: Context,
    private val graphData: GraphData
) {
    private val dispatcher = newFixedThreadPoolContext(8, "ExecGraph")
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val glDispatcher = newSingleThreadContext("Dispatchers.GL")
    private val nodes = mutableMapOf<Int, Node>()
    private val connections = mutableListOf<Connection<Any>>()

    private lateinit var surfaceView: SurfaceView

    suspend fun initialize() {
        graphData.nodes.forEach {
            val node = when (it.type) {
                NodeType.Camera -> CameraNode(
                    CameraManager(context),
                    it[Key.CameraFacing],
                    it[Key.CameraCaptureSize],
                    it[Key.CameraFrameRate]
                )
                NodeType.Microphone -> AudioSourceNode(
                    it[Key.AudioSampleRate],
                    it[Key.AudioChannel],
                    it[Key.AudioEncoding],
                    it[Key.AudioSource]
                )
                NodeType.Image -> TODO()
                NodeType.AudioFile -> TODO()
                NodeType.VideoFile -> TODO()
                NodeType.ColorFilter -> TODO()
                NodeType.ShaderFilter -> TODO()
                NodeType.Speakers -> AudioPlayerNode()
                NodeType.Screen -> SurfaceViewNode(
                    surfaceView
                )
            }
            nodes[it.id] = node
        }

        parallelJoin(nodes.values) {
            Log.d(TAG, "initialize ${it}")
            it.initialize()
            Log.d(TAG, "initialize complete ${it}")
        }

        parallelJoin(graphData.edges) { edge ->
            Log.d(TAG, "edge: $edge")

            val from = nodes[edge.fromNodeId]!!
            val to = nodes[edge.toNodeId]!!

            Log.d(TAG, "from: $from to $to")

            from.output(edge.fromKey)?.let {
                to.input(edge.toKey, it)
            }
        }

        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun start() {
        parallel(nodes.values) {
            Log.d(TAG, "start ${it}")
            it.start()
            Log.d(TAG, "start complete ${it}")
        }

        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun stop() {
        parallelJoin(nodes.values) {
            Log.d(TAG, "stop ${it}")
            it.stop()
            Log.d(TAG, "stop complete ${it}")
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun release() {
        parallelJoin(nodes.values) {
            Log.d(TAG, "release ${it}")
            it.release()
            Log.d(TAG, "release complete ${it}")
        }
        glDispatcher.close()
        dispatcher.close()
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    private fun logCoroutineInfo(job: Job?, indent: String = "") {
        job?.let {
            Log.d(TAG, "coroutine ${it[CoroutineName]}: $indent${it.children.count()}")
            it.children.forEach {
                logCoroutineInfo(it, "$indent  ")
            }
        }
    }

    private suspend fun <T> parallel(items: Iterable<T>, block: suspend (item: T) -> Unit) {
        items.forEach { scope.launch { block(it) } }
    }

    private suspend fun <T> parallelJoin(items: Iterable<T>, block: suspend (item: T) -> Unit) {
        items.map { scope.launch { block(it) } }.forEach { it.join() }
    }

    fun tmp_SetSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
    }

    companion object {
        private val TAG = Graph::class.java.simpleName
    }
}