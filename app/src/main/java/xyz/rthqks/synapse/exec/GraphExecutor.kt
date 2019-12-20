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
import xyz.rthqks.synapse.exec.edge.Config
import xyz.rthqks.synapse.exec.edge.Connection
import xyz.rthqks.synapse.exec.edge.Event
import xyz.rthqks.synapse.exec.node.*
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.logic.Edge
import xyz.rthqks.synapse.logic.Graph
import xyz.rthqks.synapse.logic.Node
import java.util.concurrent.atomic.AtomicReference

class GraphExecutor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
    private val glesManager: GlesManager,
    private val cameraManager: CameraManager,
    private val assetManager: AssetManager,
    private val graph: Graph
) {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "error", throwable)
//        throw throwable
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
    private val nodes = mutableMapOf<Int, NodeExecutor>()

    private var surfaceView: SurfaceView? = null
    private var state = AtomicReference(State.Created)

    suspend fun initialize() {
        state.getAndSet(State.Initialized).let {
            if (it != State.Created) {
                error("unexpected state $it")
            }
        }
        graph.getNodes().forEach {
            val node = nodeExecutor(it)
            nodes[it.id] = node
        }

        parallelJoin(nodes.values) {
            Log.d(TAG, "create $it")
            it.create()
            Log.d(TAG, "create complete $it")
        }

        parallelJoin(graph.getEdges()) { edge ->
            Log.d(TAG, "connect $edge")
            addEdge(edge)
            Log.d(TAG, "connect complete $edge")
        }

        parallelJoin(nodes.values) {
            Log.d(TAG, "initialize $it")
            it.initialize()
            Log.d(TAG, "initialize complete $it")
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    private fun nodeExecutor(node: Node): NodeExecutor {
        return when (node.type) {
            Node.Type.Camera -> CameraNode(
                cameraManager,
                glesManager,
                node[Key.CameraFacing] ?: CameraCharacteristics.LENS_FACING_BACK,
                node[Key.CameraCaptureSize] ?: Size(640, 480),
                node[Key.CameraFrameRate] ?: 30
            )
            Node.Type.FrameDifference -> FrameDifferenceNode(glesManager, assetManager)
            Node.Type.GrayscaleFilter -> GrayscaleNode(
                glesManager, assetManager, node[Key.ScaleFactor] ?: 1
            )
            Node.Type.BlurFilter -> BlurNode(
                glesManager,
                assetManager,
                node[Key.BlurSize] ?: 9,
                node[Key.NumPasses] ?: 1,
                node[Key.ScaleFactor] ?: 1
            )
            Node.Type.MultiplyAccumulate -> MacNode(
                glesManager,
                assetManager,
                node[Key.MultiplyFactor] ?: 0.9f,
                node[Key.AccumulateFactor] ?: 0.9f
            )
            Node.Type.OverlayFilter -> OverlayFilterNode(glesManager, assetManager)
            Node.Type.Microphone -> AudioSourceNode(
                node[Key.AudioSampleRate] ?: 48000,
                node[Key.AudioChannel] ?: AudioFormat.CHANNEL_OUT_DEFAULT,
                node[Key.AudioEncoding] ?: AudioFormat.ENCODING_PCM_16BIT,
                node[Key.AudioSource] ?: MediaRecorder.AudioSource.DEFAULT
            )
            Node.Type.Image -> TODO()
            Node.Type.AudioFile -> TODO()
            Node.Type.MediaFile -> DecoderNode(glesManager, context, node[Key.Uri] ?: "")
            Node.Type.LutFilter -> GlNode(glesManager, assetManager)
            Node.Type.ShaderFilter -> TODO()
            Node.Type.Speakers -> AudioPlayerNode()
            Node.Type.Screen -> SurfaceViewNode(assetManager, glesManager, mapOf("cropCenter" to true))

            Node.Type.Properties,
            Node.Type.Creation,
            Node.Type.Connection -> error("not an executable node type: ${node.type}")
        }
    }

    private suspend fun addEdge(edge: Edge) {
        val fromNode = nodes[edge.fromNodeId]!!
        val toNode = nodes[edge.toNodeId]!!
        val fromKey = Connection.Key<Config, Event>(edge.fromPortId)
        val toKey = Connection.Key<Config, Event>(edge.toPortId)

        val config = fromNode.getConfig(fromKey)
        toNode.setConfig(toKey, config)
        toNode.input(toKey, fromNode.output(fromKey))
    }

    suspend fun start() {
        state.getAndSet(State.Started).let {
            if (it == State.Started) {
                Log.d(TAG, "already $it")
                return
            }
            if (it != State.Initialized) {
                error("unexpected state $it")
            }
        }

        parallel(nodes.values) {
            Log.d(TAG, "start $it")
            it.start()
            Log.d(TAG, "start complete $it")
        }

        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun stop() {
        state.getAndSet(State.Initialized).let {
            if (it == State.Initialized) {
                Log.d(TAG, "already $it")
                return
            }
            if (it != State.Started) {
//                error("unexpected state $it")
            }
        }
        parallelJoin(nodes.values) {
            Log.d(TAG, "stop $it")
            it.stop()
            Log.d(TAG, "stop complete $it")
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    suspend fun release() {
        state.getAndSet(State.Released).let {
            if (it != State.Initialized) {
                error("unexpected state $it")
            }
        }
        parallelJoin(nodes.values) {
            Log.d(TAG, "release $it")
            it.release()
            Log.d(TAG, "release complete $it")
        }
        scope.cancel()
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    private fun logCoroutineInfo(job: Job?, indent: String = "") {
        job?.let {
            Log.d(TAG, "coroutine $indent${it.children.count()}")
//            it.children.forEach { child ->
//                logCoroutineInfo(child, "$indent  ")
//            }
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

    suspend fun addNode(node: Node, edge: Edge) {
        val executor = nodeExecutor(node)
        nodes[node.id] = executor
        executor.create()
        addEdge(edge)
        executor.initialize()
    }

    fun getNode(nodeId: Int): NodeExecutor = nodes[nodeId] ?: error("no node for id $nodeId")

    suspend fun add(newNodes: List<Node>, newEdges: List<Edge>) {
        val addedNodes = newNodes.map {
            val node = nodeExecutor(it)
            nodes[it.id] = node
            node
        }

        parallelJoin(addedNodes) {
            Log.d(TAG, "create $it")
            it.create()
            Log.d(TAG, "create complete $it")
        }

        parallelJoin(newEdges) { edge ->
            Log.d(TAG, "connect $edge")
            addEdge(edge)
            Log.d(TAG, "connect complete $edge")
        }

        parallelJoin(addedNodes) {
            Log.d(TAG, "initialize $it")
            it.initialize()
            Log.d(TAG, "initialize complete $it")
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    companion object {
        const val TAG = "GraphExecutor"
    }

    enum class State {
        Created,
        Initialized,
        Started,
        Released
    }
}