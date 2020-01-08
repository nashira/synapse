package com.rthqks.synapse.exec

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.node.SurfaceViewNode
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.logic.*
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Inject

class Executor @Inject constructor(
    private val context: Context
) {
    private var graphExecutor: GraphExecutor? = null
    private var graph: Graph? = null
    private val dispatcher = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    private val glesManager = GlesManager()
    private val cameraManager = CameraManager(context)
    private val assetManager = AssetManager(context)

    private val scope = CoroutineScope(Job() + dispatcher)

    private var preview = false

    private val commandChannel = scope.actor<Operation>(capacity = Channel.UNLIMITED) {
        for (msg in channel) {
            Log.d(TAG, "handling $msg")
            withTimeoutOrNull(msg.timeout) {
                when (msg) {
                    is Operation.Initialize -> doInitialize(msg.isPreview)
                    is Operation.InitGraph -> doInitializeGraph(msg.graph)
                    is Operation.ReleaseGraph -> doReleaseGraph()
                    is Operation.Release -> doRelease()
                    is Operation.Start -> doStart()
                    is Operation.Stop -> doStop()
                    is Operation.ConnectPreview -> doAddConnectionPreviews(msg.source, msg.targets)
                    is Operation.SetSurfaceView -> doSetSurfaceView(msg.nodeId, msg.surfaceView)
                    is Operation.Wait -> msg.deferred.complete(Unit)
                }
            } ?: run {
                Log.w(TAG, "TIMEOUT handling $msg")
            }
            Log.d(TAG, "done handling $msg")
        }
    }

    fun initialize(preview: Boolean) {
        runBlocking {
            commandChannel.send(Operation.Initialize(preview))
        }
    }

    fun initializeGraph(graph: Graph) {
        runBlocking {
            commandChannel.send(Operation.InitGraph(graph))
        }
    }

    fun release() {
        runBlocking {
            commandChannel.send(Operation.Release())
        }
    }

    fun releaseGraph() {
        runBlocking {
            commandChannel.send(Operation.ReleaseGraph())
        }
    }

    fun start() {
        runBlocking {
            commandChannel.send(Operation.Start())
        }
    }

    fun stop() {
        runBlocking {
            commandChannel.send(Operation.Stop())
        }
    }

    fun addConnectionPreviews(source: Connector, targets: List<Connector>) {
        runBlocking {
            commandChannel.send(Operation.ConnectPreview(source, targets))
        }
    }

    fun setPreviewSurfaceView(nodeId: Int, surfaceView: SurfaceView) {
        runBlocking {
            commandChannel.send(Operation.SetSurfaceView(nodeId, surfaceView))
        }
    }

    suspend fun await() {
        val deferred = CompletableDeferred<Unit>()
        commandChannel.send(Operation.Wait(deferred))
        deferred.await()
    }

    private suspend fun doSetSurfaceView(nodeId: Int, surfaceView: SurfaceView) {
        val graphExecutor = graphExecutor ?: return
        val nodes = LinkedList<Pair<Int, NodeExecutor>>()
        nodes.add(nodeId to graphExecutor.getNode(nodeId))

        do {
            val (id, node) = nodes.remove()
            Log.d(TAG, "looking for surfaceview at $id")
            if (node is SurfaceViewNode) {
                Log.d(TAG, "setting surfaceview on $id")
                node.setSurfaceView(surfaceView)
                return
            }

            graph?.getEdges(id)?.forEach {
                if (it.fromNodeId == id && it.toNodeId > Graph.COPY_ID_SKIP) {
                    nodes.add(it.toNodeId to graphExecutor.getNode(it.toNodeId))
                }
            }
        } while (nodes.isNotEmpty())
    }

    fun setSurfaceView(surfaceView: SurfaceView) {

    }

    private suspend fun doInitialize(preview: Boolean) {
        this.preview = preview
        glesManager.withGlContext {
            it.initialize()
        }
        cameraManager.initialize()
    }

    private suspend fun doRelease() {
        cameraManager.release()
        glesManager.withGlContext {
            it.release()
        }
        commandChannel.close()
        scope.cancel()
        dispatcher.close()
    }

    private suspend fun doStart() {
        graphExecutor?.start()
    }

    private suspend fun doStop() {
        graphExecutor?.stop()
    }

    private suspend fun doInitializeGraph(graph: Graph) {
        Log.d(TAG, "initialize graph ${graph.id}")
        var graphNew = graph
        if (preview) {
            graphNew = graph.copy()
            graphNew.getNodes().forEach { source ->
                source.ports.values.firstOrNull {
                    it.output && it.type == Port.Type.Video
                }?.id?.let {
                    val node = NewNode(Node.Type.Screen, graphNew.id)
                    graphNew.addNode(node)
                    graphNew.addEdge(source.id, it, node.id, SurfaceViewNode.INPUT.id)
                }
            }
        }

        this.graph = graphNew

        graphExecutor = GraphExecutor(
            context,
            dispatcher,
            glesManager,
            cameraManager,
            assetManager,
            graphNew
        )
        graphExecutor?.initialize()
    }

    private suspend fun doReleaseGraph() {
        Log.d(TAG, "release graph ${graph?.id}")
        graphExecutor?.release()
    }

    private suspend fun doAddConnectionPreviews(source: Connector, targets: List<Connector>) {
        val graph = graph ?: return
        Log.d(TAG, "source node ${source.node.type} ${source.node.id} ${source.port.id}")
        val data = mutableListOf<Pair<Node, Edge>>()
        targets.forEach {
            val target = it.node
            Log.d(TAG, "adding node ${target.type} ${target.id} ${it.port.id}")
            graph.addNode(target)
            Log.d(TAG, "added node ${target.type} ${target.id} ${it.port.id}")
            val edge = graph.addEdge(source.node.id, source.port.id, target.id, it.port.id)
            data.add(target to edge)

            target.ports.values.firstOrNull {
                it.output && it.type == Port.Type.Video
            }?.id?.let {
                val screen = NewNode(Node.Type.Screen, graph.id)
                graph.addNode(screen)
                val se = graph.addEdge(target.id, it, screen.id, SurfaceViewNode.INPUT.id)
                data.add(screen to se)
            }
        }

        val (nodes, edges) = data.unzip()
        graphExecutor?.add(nodes, edges)
    }

    companion object {
        const val TAG = "Executor"
    }

    private sealed class Operation(
        val timeout: Long = 2000
    ) {
        data class Initialize(val isPreview: Boolean) : Operation()
        data class InitGraph(val graph: Graph) : Operation(10000)
        class Start : Operation()
        class Stop : Operation()
        class ReleaseGraph() : Operation()
        class Release : Operation()
        data class ConnectPreview(val source: Connector, val targets: List<Connector>) : Operation()
        data class SetSurfaceView(val nodeId: Int, val surfaceView: SurfaceView) : Operation()
        class Wait(val deferred: CompletableDeferred<Unit>) : Executor.Operation()
    }
}