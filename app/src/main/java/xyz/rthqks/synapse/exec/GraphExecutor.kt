package xyz.rthqks.synapse.exec

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import kotlinx.coroutines.*
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.data.GraphData
import xyz.rthqks.synapse.data.Key
import xyz.rthqks.synapse.exec.node.*
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.logic.Node
import java.util.concurrent.Executors

class GraphExecutor(
    private val context: Context,
    private val graphData: GraphData
) {
    private val dispatcher = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    private val exceptionHandler = CoroutineExceptionHandler { context, throwable ->
        Log.e(TAG, "error", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
    private val glesManager = GlesManager()
    private val cameraManager = CameraManager(context)
    private val assetManager = AssetManager(context)
    private val nodes = mutableMapOf<Int, NodeExecutor>()
//    private val connections = mutableListOf<Connection<Event>>()

    private lateinit var surfaceView: SurfaceView

    suspend fun initialize() {

        cameraManager.initialize()
        glesManager.withGlContext { it.initialize() }

        graphData.nodes.forEach {
            val node = when (it.type) {
                Node.Type.Camera -> CameraNode(
                    cameraManager,
                    glesManager,
                    it[Key.CameraFacing],
                    it[Key.CameraCaptureSize],
                    it[Key.CameraFrameRate]
                )
                Node.Type.FrameDifference -> FrameDifferenceNode(glesManager, assetManager)
                Node.Type.GrayscaleFilter -> GrayscaleNode(
                    glesManager, assetManager, it[Key.ScaleFactor]
                )
                Node.Type.BlurFilter -> BlurNode(
                    glesManager,
                    assetManager,
                    it[Key.BlurSize],
                    it[Key.NumPasses],
                    it[Key.ScaleFactor]
                )
                Node.Type.MultiplyAccumulate -> MacNode(
                    glesManager,
                    assetManager,
                    it[Key.MultiplyFactor],
                    it[Key.AccumulateFactor]
                )
                Node.Type.OverlayFilter -> OverlayFilterNode(glesManager, assetManager)
                Node.Type.Microphone -> AudioSourceNode(
                    it[Key.AudioSampleRate],
                    it[Key.AudioChannel],
                    it[Key.AudioEncoding],
                    it[Key.AudioSource]
                )
                Node.Type.AudioWaveform -> AudioWaveformNode(glesManager, assetManager)
                Node.Type.Image -> TODO()
                Node.Type.AudioFile -> TODO()
                Node.Type.MediaFile -> DecoderNode(glesManager, context, it[Key.Uri])
                Node.Type.LutFilter -> GlNode(glesManager, assetManager)
                Node.Type.ShaderFilter -> TODO()
                Node.Type.Speakers -> AudioPlayerNode()
                Node.Type.Screen -> SurfaceViewNode(
                    surfaceView
                )
                Node.Type.Creation -> error("not an executable node type: ${it.type}")
                Node.Type.Connection -> error("not an executable node type: ${it.type}")
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

        scope.cancel()
        dispatcher.close()
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

    suspend fun tmp_SetSurfaceView(surfaceView: SurfaceView) {
        this.surfaceView = surfaceView
        nodes.values.forEach {
            when (it) {
                is SurfaceViewNode -> it.setSurfaceView(surfaceView)
            }
        }
    }

    companion object {
        private val TAG = GraphExecutor::class.java.simpleName
    }
}