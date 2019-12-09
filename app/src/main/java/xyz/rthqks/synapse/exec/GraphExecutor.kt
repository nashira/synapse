package xyz.rthqks.synapse.exec

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.media.AudioFormat
import android.media.MediaRecorder
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import kotlinx.coroutines.*
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.data.Key
import xyz.rthqks.synapse.exec.edge.*
import xyz.rthqks.synapse.exec.node.*
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.logic.Graph
import xyz.rthqks.synapse.logic.Node
import xyz.rthqks.synapse.logic.Port
import java.util.concurrent.Executors

class GraphExecutor(
    private val context: Context,
    private val graph: Graph
) {
    private val dispatcher = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
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

        graph.getNodes().forEach {
            val node = when (it.type) {
                Node.Type.Camera -> CameraNode(
                    cameraManager,
                    glesManager,
                    it[Key.CameraFacing] ?: CameraCharacteristics.LENS_FACING_BACK,
                    it[Key.CameraCaptureSize] ?: Size(1920, 1080),
                    it[Key.CameraFrameRate] ?: 30
                )
                Node.Type.FrameDifference -> FrameDifferenceNode(glesManager, assetManager)
                Node.Type.GrayscaleFilter -> GrayscaleNode(
                    glesManager, assetManager, it[Key.ScaleFactor] ?: 1
                )
                Node.Type.BlurFilter -> BlurNode(
                    glesManager,
                    assetManager,
                    it[Key.BlurSize] ?: 9,
                    it[Key.NumPasses] ?: 1,
                    it[Key.ScaleFactor] ?: 1
                )
                Node.Type.MultiplyAccumulate -> MacNode(
                    glesManager,
                    assetManager,
                    it[Key.MultiplyFactor] ?: 0.9f,
                    it[Key.AccumulateFactor] ?: 0.9f
                )
                Node.Type.OverlayFilter -> OverlayFilterNode(glesManager, assetManager)
                Node.Type.Microphone -> AudioSourceNode(
                    it[Key.AudioSampleRate] ?: 48000,
                    it[Key.AudioChannel] ?: AudioFormat.CHANNEL_OUT_DEFAULT,
                    it[Key.AudioEncoding] ?: AudioFormat.ENCODING_PCM_16BIT,
                    it[Key.AudioSource] ?: MediaRecorder.AudioSource.DEFAULT
                )
                Node.Type.Image -> TODO()
                Node.Type.AudioFile -> TODO()
                Node.Type.MediaFile -> DecoderNode(glesManager, context, it[Key.Uri] ?: "")
                Node.Type.LutFilter -> GlNode(glesManager, assetManager)
                Node.Type.ShaderFilter -> TODO()
                Node.Type.Speakers -> AudioPlayerNode()
                Node.Type.Screen -> SurfaceViewNode(assetManager, glesManager, surfaceView)
                Node.Type.Creation -> error("not an executable node type: ${it.type}")
                Node.Type.Connection -> error("not an executable node type: ${it.type}")
            }
            nodes[it.id] = node
        }

        parallelJoin(nodes.values) {
            Log.d(TAG, "create $it")
            it.create()
            Log.d(TAG, "create complete $it")
        }

        parallelJoin(graph.getEdges()) { edge ->
            val from = nodes[edge.fromNodeId]!!
            val to = nodes[edge.toNodeId]!!

            val fromPort = graph.getNode(edge.fromNodeId).getPort(edge.fromPortId)
            val toPort = graph.getNode(edge.toNodeId).getPort(edge.toPortId)

            if (fromPort.type != toPort.type) {
                error("port types don't match $edge")
            }

            Log.d(TAG, "connect $edge")

            when (fromPort.type) {
                Port.Type.Audio -> {
                    val fromKey = Connection.Key<AudioConfig, AudioEvent>(fromPort.id)
                    val toKey = Connection.Key<AudioConfig, AudioEvent>(toPort.id)
                    doConnection(fromKey, toKey, from, to)
                }
                Port.Type.Video -> {
                    val fromKey = Connection.Key<VideoConfig, VideoEvent>(fromPort.id)
                    val toKey = Connection.Key<VideoConfig, VideoEvent>(toPort.id)
                    doConnection(fromKey, toKey, from, to)
                }
            }

//            val config = from.getConfig()
//            from.output(edge.fromPortId)?.let {
//                to.input(edge.toPortId, it)
//            }7

            Log.d(TAG, "connect complete $edge")
        }

        parallelJoin(nodes.values) {
            Log.d(TAG, "initialize $it")
            it.initialize()
            Log.d(TAG, "initialize complete $it")
        }

        logCoroutineInfo(scope.coroutineContext[Job])
    }

    private suspend fun <C : Config, E : Event> doConnection(
        fromKey: Connection.Key<C, E>,
        toKey: Connection.Key<C, E>,
        fromNode: NodeExecutor,
        toNode: NodeExecutor
    ) {
        val config = fromNode.getConfig(fromKey)
        toNode.setConfig(toKey, config)
        toNode.input(toKey, fromNode.output(fromKey))
    }

    suspend fun start() {
        parallel(nodes.values) {
            Log.d(TAG, "start $it")
            it.start()
            Log.d(TAG, "start complete $it")
        }

        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun stop() {
        parallelJoin(nodes.values) {
            Log.d(TAG, "stop $it")
            it.stop()
            Log.d(TAG, "stop complete $it")
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun release() {
        parallelJoin(nodes.values) {
            Log.d(TAG, "release $it")
            it.release()
            Log.d(TAG, "release complete $it")
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
            it.children.forEach { child ->
                logCoroutineInfo(child, "$indent  ")
            }
        }
    }

    private suspend fun <T> parallel(items: Iterable<T>, block: suspend (item: T) -> Unit) {
        items.forEach { scope.launch { block(it) } }
    }

    private suspend fun <T> parallelJoin(items: Iterable<T>, block: suspend (item: T) -> Unit) {
        items.map { scope.launch { block(it) } }.joinAll()
    }

    suspend fun tmpSetSurfaceView(surfaceView: SurfaceView) {
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