package com.rthqks.synapse.exec

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.link.Config
import com.rthqks.synapse.exec.link.Connection
import com.rthqks.synapse.exec.link.Event
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.NodeType
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

class NetworkExecutor(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher,
    private val glesManager: GlesManager,
    private val cameraManager: CameraManager,
    private val assetManager: AssetManager,
    private val network: Network
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
        network.getNodes().forEach {
            val node = nodeExecutor(it)
            nodes[it.id] = node
        }

        parallelJoin(nodes.values) {
            Log.d(TAG, "create $it")
            it.create()
            Log.d(TAG, "create complete $it")
        }

        parallelJoin(network.getLinks()) { link ->
            Log.d(TAG, "connect $link")
            addLink(link)
            Log.d(TAG, "connect complete $link")
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
            NodeType.Camera -> CameraNode(cameraManager, glesManager, node.properties)
            NodeType.FrameDifference -> FrameDifferenceNode(glesManager, assetManager)
            NodeType.GrayscaleFilter -> GrayscaleNode(glesManager, assetManager, node.properties)
            NodeType.BlurFilter -> BlurNode(glesManager, assetManager, node.properties)
            NodeType.MultiplyAccumulate -> MacNode(glesManager, assetManager, node.properties)
            NodeType.OverlayFilter -> OverlayFilterNode(glesManager, assetManager)
            NodeType.Microphone -> AudioSourceNode(node.properties)
            NodeType.Image -> TODO()
            NodeType.AudioFile -> TODO()
            NodeType.MediaFile -> DecoderNode(glesManager, context, node.properties)
            NodeType.LutFilter -> GlNode(glesManager, assetManager)
            NodeType.ShaderFilter -> TODO()
            NodeType.Speakers -> AudioPlayerNode()
            NodeType.Screen -> SurfaceViewNode(assetManager, glesManager, node.properties + network.properties)

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

    suspend fun addNode(node: Node, link: Link) {
        val executor = nodeExecutor(node)
        nodes[node.id] = executor
        executor.create()
        addLink(link)
        executor.initialize()
    }

    fun getNode(nodeId: Int): NodeExecutor = nodes[nodeId] ?: error("no node for id $nodeId")

    suspend fun add(newNodes: List<Node>, newLinks: List<Link>) {
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