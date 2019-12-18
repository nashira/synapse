package xyz.rthqks.synapse.exec

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import xyz.rthqks.synapse.assets.AssetManager
import xyz.rthqks.synapse.exec.node.SurfaceViewNode
import xyz.rthqks.synapse.gl.GlesManager
import xyz.rthqks.synapse.logic.Graph
import xyz.rthqks.synapse.logic.Node
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Inject

class Executor @Inject constructor(
    private val context: Context
) {
    private var graphExecutor: GraphExecutor? = null
    private val dispatcher = Executors.newFixedThreadPool(6).asCoroutineDispatcher()
    private val glesManager = GlesManager()
    private val cameraManager = CameraManager(context)
    private val assetManager = AssetManager(context)

    private val scope = CoroutineScope(Job() + dispatcher)

    private var preview = false

    private val commandChannel = scope.actor<Operation>(capacity = Channel.UNLIMITED) {
        for (msg in channel) {
            when (msg) {
                is Operation.Initialize -> doInitialize(msg.isPreview)
                is Operation.InitGraph -> doInitializeGraph(msg.graph)
                is Operation.ReleaseGraph -> doReleaseGraph(msg.graph)
                is Operation.Release -> doRelease()
                is Operation.Start -> doStart()
                is Operation.Stop -> doStop()
            }
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

    fun releaseGraph(graph: Graph) {
        runBlocking {
            commandChannel.send(Operation.ReleaseGraph(graph))
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

    suspend fun setPreviewSurfaceView(graph: Graph, nodeId: Int, surfaceView: SurfaceView) {
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

            graph.getEdges(id).forEach {
                if (it.fromNodeId == id) {
                    nodes.add(it.toNodeId to graphExecutor.getNode(it.toNodeId))
                }
            }
        } while (nodes.isNotEmpty())
    }

    fun setSurfaceView(surfaceView: SurfaceView) {

    }

    private suspend fun doInitialize(preview: Boolean) {
        this.preview = preview
        Log.d(TAG, "initialize")
        glesManager.withGlContext {
            glesManager.initialize()
        }
        cameraManager.initialize()
    }

    private suspend fun doRelease() {
        Log.d(TAG, "release")
        cameraManager.release()
        glesManager.withGlContext {
            glesManager.release()
        }
        commandChannel.close()
        scope.cancel()
        dispatcher.close()
    }

    private suspend fun doStart() {
        Log.d(TAG, "start")
        graphExecutor?.start()
    }

    private suspend fun doStop() {
        Log.d(TAG, "stop")
        graphExecutor?.stop()
    }

    private suspend fun doInitializeGraph(graph: Graph) {
        Log.d(TAG, "initialize graph ${graph.id}")
        graph.getNodes().forEach { source ->
            if (source.type != Node.Type.Screen) {
                val portId = source.ports.values.firstOrNull { it.output }?.id
                portId?.let {
                    val node = Node.Type.Screen.node().copy(graph.id)
                    graph.addNode(node)
                    graph.addEdge(source.id, portId, node.id, SurfaceViewNode.INPUT.id)
                }
            }
        }

        graphExecutor = GraphExecutor(
            context,
            dispatcher,
            glesManager,
            cameraManager,
            assetManager,
            graph
        )
        graphExecutor?.initialize()
    }

    private suspend fun doReleaseGraph(graph: Graph) {
        Log.d(TAG, "release graph ${graph.id}")
        graphExecutor?.release()
    }

    companion object {
        const val TAG = "Executor"
    }

    private sealed class Operation {
        class Initialize(val isPreview: Boolean) : Operation()
        class InitGraph(val graph: Graph) : Operation()
        class Start : Operation()
        class Stop : Operation()
        class ReleaseGraph(val graph: Graph) : Operation()
        class Release : Operation()
    }
}