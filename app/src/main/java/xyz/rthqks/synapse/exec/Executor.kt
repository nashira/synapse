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
import xyz.rthqks.synapse.logic.Port
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
            when (msg) {
                is Operation.Initialize -> doInitialize(msg.isPreview)
                is Operation.InitGraph -> doInitializeGraph(msg.graph)
                is Operation.ReleaseGraph -> doReleaseGraph()
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

    suspend fun setPreviewSurfaceView(nodeId: Int, surfaceView: SurfaceView) {
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
        var graphNew = graph
        if (preview) {
            graphNew = graph.copy()
            graphNew.getNodes().forEach { source ->
                source.ports.values.firstOrNull {
                    it.output && it.type == Port.Type.Video
                }?.id?.let {
                    val node = Node.Type.Screen.node().copy(graphNew.id)
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

    companion object {
        const val TAG = "Executor"
    }

    private sealed class Operation {
        class Initialize(val isPreview: Boolean) : Operation()
        class InitGraph(val graph: Graph) : Operation()
        class Start : Operation()
        class Stop : Operation()
        class ReleaseGraph() : Operation()
        class Release : Operation()
    }
}