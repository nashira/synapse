package com.rthqks.synapse.exec

import android.util.Log
import android.view.SurfaceView
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.assets.VideoStorage
import com.rthqks.synapse.exec.link.Config
import com.rthqks.synapse.exec.link.Connection
import com.rthqks.synapse.exec.link.Event
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.NodeType
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class NetworkExecutor(
    context: ExecutionContext,
    private val network: Network
): Executor(context) {
    private val glesManager: GlesManager = context.glesManager
    private val cameraManager: CameraManager = context.cameraManager
    private val assetManager: AssetManager = context.assetManager
    private val videoStorage: VideoStorage = context.videoStorage

//    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
//        Log.e(TAG, "error", throwable)
//        throw throwable
//    }
//
//    private val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
    private val nodes = mutableMapOf<Int, NodeExecutor>()

    private var surfaceView: SurfaceView? = null
    private var state = AtomicReference(State.Created)

    suspend fun initialize() = await {
        state.getAndSet(State.Initialized).let {
            if (it != State.Created) {
                error("unexpected state $it")
            }
        }
        network.getNodes().forEach {
            val node = nodeExecutor(it)
            nodes[it.id] = node
        }

        Log.d(TAG, "create start")
        parallelJoin(nodes.values) {
            Log.d(TAG, "create $it")
            it.create()
            Log.d(TAG, "create complete $it")
        }
        Log.d(TAG, "create done")

        Log.d(TAG, "connect start")
        prepareLinks()

        parallelJoin(network.getLinks()) { link ->
            Log.d(TAG, "connect $link")
            addLink(link)
            Log.d(TAG, "connect complete $link")
        }
        Log.d(TAG, "connect done")

        Log.d(TAG, "initialize start")
        parallelJoin(nodes.values) {
            Log.d(TAG, "initialize $it")
            it.initialize()
            Log.d(TAG, "initialize complete $it")
        }
        Log.d(TAG, "initialize done")
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    private fun prepareLinks() {
        network.getLinks().forEach { link ->
            val fromKey = Connection.Key<Config, Event>(link.fromPortId)
            val toKey = Connection.Key<Config, Event>(link.toPortId)

            val fromNode = nodes[link.fromNodeId]!!
            val toNode = nodes[link.toNodeId]!!
            fromNode.setLinked(fromKey)
            toNode.setLinked(toKey)

            Log.d(TAG, "link ${link.fromNodeId} ${link.fromPortId} ${link.toNodeId} ${link.toPortId} ${link.inCycle}")
            if (link.inCycle) {
                fromNode.setCycle(fromKey)
                toNode.setCycle(toKey)
            }
        }
    }

    private fun nodeExecutor(node: Node): NodeExecutor {
        return when (node.type) {
            NodeType.Camera -> CameraNode(cameraManager, glesManager, node.properties)
            NodeType.FrameDifference -> FrameDifferenceNode(glesManager, assetManager)
            NodeType.GrayscaleFilter -> GrayscaleNode(glesManager, assetManager, node.properties)
            NodeType.BlurFilter -> BlurNode(glesManager, assetManager, node.properties)
            NodeType.MultiplyAccumulate -> MacNode(glesManager, assetManager, node.properties)
            NodeType.Microphone -> AudioSourceNode(node.properties)
            NodeType.Image -> ImageSourceNode(context.context, glesManager, node.properties)
            NodeType.CubeImport -> CubeImportNode(context.context, glesManager, node.properties)
            NodeType.AudioFile -> TODO()
            NodeType.MediaFile -> DecoderNode(glesManager, context.context, node.properties)
            NodeType.Lut2d -> Lut2dNode(assetManager, glesManager, node.properties)
            NodeType.Lut3d -> Lut3dNode(assetManager, glesManager, node.properties)
            NodeType.ShaderFilter -> TODO()
            NodeType.Speakers -> AudioPlayerNode()
            NodeType.Screen -> SurfaceViewNode(scope, assetManager, glesManager, node.properties + network.properties)
            NodeType.SlimeMold -> PhysarumNode(assetManager, glesManager, node.properties)
            NodeType.ImageBlend -> ImageBlendNode(assetManager, glesManager, node.properties)
            NodeType.CropResize -> CropResizeNode(assetManager, glesManager, node.properties)
            NodeType.Shape -> ShapeNode(assetManager, glesManager, node.properties)
            NodeType.RingBuffer -> RingBufferNode(assetManager, glesManager, node.properties)
            NodeType.Slice3d -> Slice3dNode(assetManager, glesManager, node.properties)
            NodeType.MediaEncoder -> EncoderNode(assetManager, glesManager, videoStorage, node.properties)
            NodeType.RotateMatrix -> RotateMatrixNode(node.properties)
            NodeType.Properties,
            NodeType.Creation,
            NodeType.Connection -> error("not an executable node type: ${node.type}")
        }
    }

    private suspend fun addLink(link: Link) {
        val fromNode = nodes[link.fromNodeId]!!
        val toNode = nodes[link.toNodeId]!!
        val fromKey = Connection.Key<Config, Event>(link.fromPortId)
        val toKey = Connection.Key<Config, Event>(link.toPortId)

        toNode.setConfig(toKey, fromNode.getConfig(fromKey))
        toNode.input(toKey, fromNode.output(fromKey))
    }

    suspend fun start() = await {
        state.getAndSet(State.Started).let {
            if (it == State.Started) {
                Log.d(TAG, "already $it")
                return@await
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

    suspend fun stop() = await {
        state.getAndSet(State.Initialized).let {
            if (it == State.Initialized) {
                Log.d(TAG, "already $it")
                return@await
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

    override suspend fun release() {
        await {
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
        }
        super.release()
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

    suspend fun tmpSetSurfaceView(surfaceView: SurfaceView) = await {
        this.surfaceView = surfaceView
        nodes.values.forEach {
            when (it) {
                is SurfaceViewNode -> it.setSurfaceView(surfaceView)
            }
        }
    }

    suspend fun addNode(node: Node, link: Link) {
        val executor = nodeExecutor(node)
        nodes[node.id] = executor
        executor.create()
        addLink(link)
        executor.initialize()
    }

    fun getNode(nodeId: Int): NodeExecutor = nodes[nodeId] ?: error("no node for id $nodeId")

    suspend fun add(newNodes: List<Node>, newLinks: List<Link>) = await {
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

        prepareLinks()
        parallelJoin(newLinks) { link ->
            Log.d(TAG, "connect $link")
            addLink(link)
            Log.d(TAG, "connect complete $link")
        }

        parallelJoin(addedNodes) {
            Log.d(TAG, "initialize $it")
            it.initialize()
            Log.d(TAG, "initialize complete $it")
        }
        logCoroutineInfo(scope.coroutineContext[Job])
    }

    companion object {
        const val TAG = "NetworkExecutor"
    }

    enum class State {
        Created,
        Initialized,
        Started,
        Released
    }
}