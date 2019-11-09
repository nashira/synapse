package xyz.rthqks.synapse.core

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import kotlinx.coroutines.*
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.core.node.*
import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.Key
import xyz.rthqks.synapse.data.NodeType
import xyz.rthqks.synapse.gl.GlesManager
import java.util.concurrent.Executors

class Graph(
    private val context: Context,
    private val graphData: GraphData
) {
    private val dispatcher = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    private val exceptionHandler = CoroutineExceptionHandler { context, throwable ->
        Log.d(TAG, "error", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
    private val glesManager = GlesManager()
    private val cameraManager = CameraManager(context)
    private val assetManager = AssetManager(context)
    private val nodes = mutableMapOf<Int, Node>()
    private val connections = mutableListOf<Connection<Any>>()

    private lateinit var surfaceView: SurfaceView

    suspend fun initialize() {

        cameraManager.initialize()
        glesManager.withGlContext { it.initialize() }

        graphData.nodes.forEach {
            val node = when (it.type) {
                NodeType.Camera -> CameraNode(
                    cameraManager,
                    glesManager,
                    it[Key.CameraFacing],
                    it[Key.CameraCaptureSize],
                    it[Key.CameraFrameRate]
                )
                NodeType.FrameDifference -> FrameDifferenceNode(glesManager, assetManager)
                NodeType.GrayscaleFilter -> GrayscaleNode(glesManager, assetManager)
                NodeType.BlurFilter -> BlurNode(glesManager, assetManager)
                NodeType.OverlayFilter -> OverlayFilterNode(glesManager, assetManager)
                NodeType.Microphone -> AudioSourceNode(
                    it[Key.AudioSampleRate],
                    it[Key.AudioChannel],
                    it[Key.AudioEncoding],
                    it[Key.AudioSource]
                )
                NodeType.AudioWaveform -> AudioWaveformNode(glesManager, assetManager)
                NodeType.Image -> TODO()
                NodeType.AudioFile -> TODO()
                NodeType.VideoFile -> TODO()
                NodeType.LutFilter -> GlNode(glesManager, assetManager)
                NodeType.ShaderFilter -> TODO()
                NodeType.Speakers -> AudioPlayerNode()
                NodeType.Screen -> SurfaceViewNode(
                    surfaceView
                )
            }
            nodes[it.id] = node
        }

        parallelJoin(nodes.values) {
            Log.d(TAG, "create ${it}")
            it.create()
            Log.d(TAG, "create complete ${it}")
        }

        parallelJoin(graphData.edges) { edge ->
            val from = nodes[edge.fromNodeId]!!
            val to = nodes[edge.toNodeId]!!

            Log.d(TAG, "connect $edge")
            from.output(edge.fromKey)?.let {
                to.input(edge.toKey, it)
            }
            Log.d(TAG, "connect complete $edge")
        }

        parallelJoin(nodes.values) {
            Log.d(TAG, "initialize ${it}")
            it.initialize()
            Log.d(TAG, "initialize complete ${it}")
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
        cameraManager.release()
        glesManager.withGlContext {
            it.release()
        }

        dispatcher.close()
        scope.cancel()
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    private fun logCoroutineInfo(job: Job?, indent: String = "") {
        job?.let {
            Log.d(TAG, "coroutine $indent${it.children.count()}")
            it.children.forEach {
                logCoroutineInfo(it, "$indent  ")
            }
        }
    }

    private suspend fun <T> parallel(items: Iterable<T>, block: suspend (item: T) -> Unit) {
        items.forEach { scope.launch { block(it) } }
    }

    private suspend fun <T> parallelJoin(items: Iterable<T>, block: suspend (item: T) -> Unit) {
        items.map { scope.launch { block(it) } }.joinAll()
    }

    fun tmp_SetSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
    }

    companion object {
        private val TAG = Graph::class.java.simpleName
    }
}