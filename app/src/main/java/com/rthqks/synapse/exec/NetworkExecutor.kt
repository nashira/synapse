package com.rthqks.synapse.exec

import android.util.Log
import android.view.SurfaceView
import com.rthqks.synapse.exec.link.Config
import com.rthqks.synapse.exec.link.Connection
import com.rthqks.synapse.exec.link.Event
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.NodeType
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

class NetworkExecutor(
    context: ExecutionContext,
    private val network: Network
): Executor(context) {
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
        nodes.values.map {
            Log.d(TAG, "create $it")
            val deferred = it.create()
            Log.d(TAG, "create sent $it")
            deferred
        }.awaitAll()
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
        nodes.values.map {
            Log.d(TAG, "initialize $it")
            val deferred = it.inititalize()
            Log.d(TAG, "initialize sent $it")
            deferred
        }.awaitAll()
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
            NodeType.Camera -> CameraNode(context, node.properties)
            NodeType.FrameDifference -> FrameDifferenceNode(context, node.properties)
            NodeType.GrayscaleFilter -> GrayscaleNode(context, node.properties)
            NodeType.BlurFilter -> BlurNode(context, node.properties)
            NodeType.MultiplyAccumulate -> MacNode(context, node.properties)
            NodeType.Microphone -> AudioSourceNode(context, node.properties)
            NodeType.Image -> ImageSourceNode(context, node.properties)
            NodeType.CubeImport -> CubeImportNode(context, node.properties)
            NodeType.AudioFile -> TODO()
            NodeType.MediaFile -> DecoderNode(context, node.properties)
            NodeType.Lut2d -> Lut2dNode(context, node.properties)
            NodeType.Lut3d -> Lut3dNode(context, node.properties)
            NodeType.ShaderFilter -> TODO()
            NodeType.Speakers -> AudioPlayerNode(context)
            NodeType.Screen -> SurfaceViewNode(context, node.properties + network.properties)
            NodeType.SlimeMold -> PhysarumNode(context, node.properties)
            NodeType.ImageBlend -> ImageBlendNode(context, node.properties)
            NodeType.CropResize -> CropResizeNode(context, node.properties)
            NodeType.Shape -> ShapeNode(context, node.properties)
            NodeType.RingBuffer -> RingBufferNode(context, node.properties)
            NodeType.Slice3d -> Slice3dNode(context, node.properties)
            NodeType.MediaEncoder -> EncoderNode(context, node.properties)
            NodeType.RotateMatrix -> RotateMatrixNode(context, node.properties)
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
        nodes.values.map {
            Log.d(TAG, "start $it")
            val deferred = it.start()
            Log.d(TAG, "start sent $it")
            deferred
        }.awaitAll()
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
        nodes.values.map {
            Log.d(TAG, "stop $it")
            val deferred = it.stop()
            Log.d(TAG, "stop sent $it")
            deferred
        }.awaitAll()
//        parallelJoin(nodes.values) {
//            Log.d(TAG, "stop $it")
//            it.onStop()
//            Log.d(TAG, "stop complete $it")
//        }
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
        executor.onCreate()
        addLink(link)
        executor.onInitialize()
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
            it.onCreate()
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
            it.onInitialize()
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