package com.rthqks.synapse.exec

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.rthqks.synapse.assets.AssetManager
import com.rthqks.synapse.exec.node.SurfaceViewNode
import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import java.util.*
import java.util.concurrent.Executors
import javax.inject.Inject

class Executor @Inject constructor(
    private val context: Context
) {
    private var networkExecutor: NetworkExecutor? = null
    private var network: Network? = null
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
                    is Operation.InitNetwork -> doInitializeNetwork(msg.network)
                    is Operation.ReleaseNetwork -> doReleaseNetwork()
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

    fun initializeNetwork(network: Network) {
        runBlocking {
            commandChannel.send(Operation.InitNetwork(network))
        }
    }

    fun release() {
        runBlocking {
            commandChannel.send(Operation.Release())
        }
    }

    fun releaseNetwork() {
        runBlocking {
            commandChannel.send(Operation.ReleaseNetwork())
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
        val networkExecutor = networkExecutor ?: return
        val nodes = LinkedList<Pair<Int, NodeExecutor>>()
        nodes.add(nodeId to networkExecutor.getNode(nodeId))

        do {
            val (id, node) = nodes.remove()
            Log.d(TAG, "looking for surfaceview at $id")
            if (node is SurfaceViewNode) {
                Log.d(TAG, "setting surfaceview on $id")
                node.setSurfaceView(surfaceView)
                return
            }

            network?.getLinks(id)?.forEach {
                if (it.fromNodeId == id && it.toNodeId > Network.COPY_ID_SKIP) {
                    nodes.add(it.toNodeId to networkExecutor.getNode(it.toNodeId))
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
        networkExecutor?.start()
    }

    private suspend fun doStop() {
        networkExecutor?.stop()
    }

    private suspend fun doInitializeNetwork(network: Network) {
        Log.d(TAG, "initialize network ${network.id}")
        var networkNew = network
        if (preview) {
            networkNew = network.copy()
            networkNew.getNodes().forEach { source ->
                source.ports.values.firstOrNull {
                    it.output && it.type == Port.Type.Video
                }?.id?.let {
                    val node = NewNode(Node.Type.Screen, networkNew.id)
                    networkNew.addNode(node)
                    networkNew.addLink(source.id, it, node.id, SurfaceViewNode.INPUT.id)
                }
            }
        }

        this.network = networkNew

        networkExecutor = NetworkExecutor(
            context,
            dispatcher,
            glesManager,
            cameraManager,
            assetManager,
            networkNew
        )
        networkExecutor?.initialize()
    }

    private suspend fun doReleaseNetwork() {
        Log.d(TAG, "release network ${network?.id}")
        networkExecutor?.release()
    }

    private suspend fun doAddConnectionPreviews(source: Connector, targets: List<Connector>) {
        val network = network ?: return
        Log.d(TAG, "source node ${source.node.type} ${source.node.id} ${source.port.id}")
        val data = mutableListOf<Pair<Node, Link>>()
        targets.forEach {
            val target = it.node
            Log.d(TAG, "adding node ${target.type} ${target.id} ${it.port.id}")
            network.addNode(target)
            Log.d(TAG, "added node ${target.type} ${target.id} ${it.port.id}")
            val link = network.addLink(source.node.id, source.port.id, target.id, it.port.id)
            data.add(target to link)

            target.ports.values.firstOrNull {
                it.output && it.type == Port.Type.Video
            }?.id?.let {
                val screen = NewNode(Node.Type.Screen, network.id)
                network.addNode(screen)
                val se = network.addLink(target.id, it, screen.id, SurfaceViewNode.INPUT.id)
                data.add(screen to se)
            }
        }

        val (nodes, links) = data.unzip()
        networkExecutor?.add(nodes, links)
    }

    companion object {
        const val TAG = "Executor"
    }

    private sealed class Operation(
        val timeout: Long = 2000
    ) {
        data class Initialize(val isPreview: Boolean) : Operation()
        data class InitNetwork(val network: Network) : Operation(10000)
        class Start : Operation()
        class Stop : Operation()
        class ReleaseNetwork() : Operation()
        class Release : Operation()
        data class ConnectPreview(val source: Connector, val targets: List<Connector>) : Operation()
        data class SetSurfaceView(val nodeId: Int, val surfaceView: SurfaceView) : Operation()
        class Wait(val deferred: CompletableDeferred<Unit>) : Executor.Operation()
    }
}