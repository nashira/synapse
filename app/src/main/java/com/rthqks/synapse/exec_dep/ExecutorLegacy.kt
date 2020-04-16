package com.rthqks.synapse.exec_dep

import android.os.SystemClock
import android.util.Log
import android.view.SurfaceView
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.logic.Connector
import com.rthqks.synapse.logic.Network
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import java.util.*
import javax.inject.Inject

class ExecutorLegacy @Inject constructor(
    val context: ExecutionContext
) {
    private var network: Network? = null
    private val dispatcher = context.dispatcher
    private val glesManager = context.glesManager
    private val cameraManager = context.cameraManager
    private val assetManager = context.assetManager

    val deviceSupported: Boolean get() = glesManager.supportedDevice

    private val scope = CoroutineScope(Job() + dispatcher)

    private var preview = false

    private val commandChannel = scope.actor<Operation>(capacity = Channel.UNLIMITED) {
        for (msg in channel) {
            val start = SystemClock.elapsedRealtime()
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
                    is Operation.SetSurfaceView -> doSetSurfaceView(
                        msg.nodeId,
                        msg.portId,
                        msg.surfaceView
                    )
                    is Operation.SetSurfaceViewNetwork -> doSetSurfaceViewNetwork(msg.surfaceView)
                    is Operation.Wait -> msg.deferred.complete(Unit)
                }
            } ?: run {
                Log.w(TAG, "TIMEOUT handling $msg")
            }
            Log.d(TAG, "done handling $msg ${SystemClock.elapsedRealtime() - start}ms")
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

    fun setPreviewSurfaceView(nodeId: Int, portId: String?, surfaceView: SurfaceView) {
        runBlocking {
            commandChannel.send(Operation.SetSurfaceView(nodeId, portId, surfaceView))
        }
    }

    fun setSurfaceView(surfaceView: SurfaceView) {
        runBlocking {
            commandChannel.send(Operation.SetSurfaceViewNetwork(surfaceView))
        }
    }

    suspend fun await() {
        val deferred = CompletableDeferred<Unit>()
        commandChannel.send(Operation.Wait(deferred))
        deferred.await()
    }

    private suspend fun async(): CompletableDeferred<Unit> {
        val deferred = CompletableDeferred<Unit>()
        commandChannel.send(Operation.Wait(deferred))
        return deferred
    }

    private suspend fun doSetSurfaceViewNetwork(surfaceView: SurfaceView) {
    }

    private suspend fun doSetSurfaceView(nodeId: Int, portId: String?, surfaceView: SurfaceView) {
//        val networkExecutor = networkExecutor ?: return
        var firstPortId = portId
        val nodes = LinkedList<Pair<Int, NodeExecutor>>()
//        nodes.add(nodeId to networkExecutor.getNode(nodeId))
        do {
            val (id, node) = nodes.remove()
            Log.d(TAG, "looking for surfaceview at $id")
//            if (node is SurfaceViewNode) {
//                Log.d(TAG, "setting surfaceview on $id")
//                node.setSurfaceView(surfaceView)
//                return
//            }

            network?.getLinks(id)?.forEach {
                //                it
                if (it.fromNodeId == id
                    && (firstPortId == null || it.fromPortId == firstPortId)
                    && it.toNodeId > Network.COPY_ID_SKIP
                ) {
//                    nodes.add(it.toNodeId to networkExecutor.getNode(it.toNodeId))
                }
            }
            firstPortId = null
        } while (nodes.isNotEmpty())
    }

    private suspend fun doInitialize(preview: Boolean) {
        this.preview = preview
        glesManager.glContext {
            it.initialize()
        }
        cameraManager.initialize()
    }

    private suspend fun doRelease() {
        commandChannel.close()
        context.release()
        scope.cancel()
    }

    private suspend fun doStart() {
//        networkExecutor?.start()
    }

    private suspend fun doStop() {
//        networkExecutor?.stop()
    }

    private suspend fun doInitializeNetwork(network: Network) {
        Log.d(TAG, "initialize network ${network.id}")
        if (!deviceSupported) {
            return
        }
        var networkNew = network
//        if (preview) {
//            networkNew = network.copy()
//            val links = networkNew.getNodes().map { source ->
//                source.ports.values.filter {
//                    it.output && it.type == Port.Type.Video
//                }.map {
//                    val node = NewNode(NodeType.Screen)
//                    networkNew.addNode(node)
//                    Link(source.id, it.id, node.id, SurfaceViewNode.INPUT.id)
//                }
//            }.flatten()
//
//            networkNew.addLinks(links)
//        }

        this.network = networkNew

//        networkExecutor = NetworkExecutor(context, networkNew)
//        networkExecutor?.initialize()
    }

    private suspend fun doReleaseNetwork() {
        Log.d(TAG, "release network ${network?.id}")
//        networkExecutor?.release()
    }

    private suspend fun doAddConnectionPreviews(source: Connector, targets: List<Connector>) {
//        val network = network ?: return
//        Log.d(TAG, "source node ${source.node.type} ${source.node.id} ${source.port.id}")
//        if (!source.port.output) {
//            return
//        }
//        val data = mutableListOf<Pair<Node, Link>>()
//        var sourceNode = source.node
//
//        if (source.port.type == Port.Type.Video) {
//            val cropNode = NewNode(NodeType.CropResize)
//            sourceNode = cropNode
//            network.addNode(cropNode)
//            val se = Link(source.node.id, source.port.id, cropNode.id, CropResizeNode.INPUT.id)
//            network.addLinkNoCompute(se)
//            data += Pair(cropNode, se)
//        }
//
//        targets.forEach {
//            val target = it.node
//            Log.d(TAG, "adding node ${target.type} ${target.id} ${it.port.id}")
//            network.addNode(target)
//            Log.d(TAG, "added node ${target.type} ${target.id} ${it.port.id}")
//            val link = Link(sourceNode.id, CropResizeNode.OUTPUT.id, target.id, it.port.id)
//            network.addLinkNoCompute(link)
//            data.add(target to link)
//
//            target.ports.values.firstOrNull {
//                it.output && it.type == Port.Type.Video
//            }?.id?.let {
//                val screen = NewNode(NodeType.Screen)
//                network.addNode(screen)
//                val se = Link(target.id, it, screen.id, SurfaceViewNode.INPUT.id)
//                network.addLinkNoCompute(se)
//                data.add(screen to se)
//            }
//        }
//        network.computeComponents()
//
//        val (nodes, links) = data.unzip()
//        networkExecutor?.add(nodes, links)
    }

    companion object {
        const val TAG = "ExecutorLegacy"
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
        data class SetSurfaceView(
            val nodeId: Int,
            val portId: String?,
            val surfaceView: SurfaceView
        ) : Operation()

        class SetSurfaceViewNetwork(val surfaceView: SurfaceView) : Operation()
        class Wait(val deferred: CompletableDeferred<Unit>) : ExecutorLegacy.Operation()
    }
}