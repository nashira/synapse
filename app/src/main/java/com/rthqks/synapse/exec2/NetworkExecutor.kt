package com.rthqks.synapse.exec2

import android.util.Log
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.Executor
import com.rthqks.synapse.exec2.node.*
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.NodeType
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

open class NetworkExecutor(context: ExecutionContext) : Executor(context) {
    private val nodes = ConcurrentHashMap<Int, NodeExecutor>()
    protected var network: Network? = null
    var isResumed = false
        private set

    open suspend fun setup() = await {
        context.setup()
    }

    open suspend fun pause() = await {
        isResumed = false
        nodes.map {
            scope.launch { it.value.pause() }
        }.joinAll()
    }

    open suspend fun resume() = await {
        isResumed = true
        nodes.map {
            scope.launch { it.value.resume() }
        }.joinAll()
    }

    fun getNode(id: Int) = nodes[id]

    suspend fun addNode(node: Node) {
        Log.d(TAG, "add node ${node.id}")
        val executor = node.executor()
        nodes[node.id] = executor
        network?.getLinks(node.id)?.forEach {
            val key = if (it.fromNodeId == node.id) {
                Connection.Key<Any?>(it.fromPortId)
            } else {
                Connection.Key(it.toPortId)
            }
            executor.setLinked(key)
            if (it.inCycle) {
                executor.setCycle(key)
            }
        }
        executor.setup().await()
        if (isResumed) {
            executor.resume()
        }
    }

    suspend fun removeNode(node: Node) {
        Log.d(TAG, "remove node ${node.id}")
        nodes.remove(node.id)?.let {
            it.pause()
            it.release()
        }
    }

    suspend fun addLink(link: Link) {
        val fromNode = nodes[link.fromNodeId] ?: error("missing node ${link.fromNodeId}")
        val toNode = nodes[link.toNodeId] ?: error("missing node ${link.toNodeId}")
        val fromKey = Connection.Key<Any?>(link.fromPortId)
        val toKey = Connection.Key<Any?>(link.toPortId)

        Log.d(TAG, "add link $link")

        val channel = fromNode.getConsumer(fromKey).await()
        val consume = toNode.startConsumer(toKey, channel as ReceiveChannel<Message<Any?>>)

        consume.await()
    }

    suspend fun removeLink(link: Link) {
        val fromNode = nodes[link.fromNodeId] ?: error("missing node ${link.fromNodeId}")
        val toNode = nodes[link.toNodeId] ?: error("missing node ${link.toNodeId}")
        val fromKey = Connection.Key<Any?>(link.fromPortId)
        val toKey = Connection.Key<Any?>(link.toPortId)

        val channel = toNode.channel(toKey)!!

        Log.d(TAG, "remove link $link")

        fromNode.setCycle(fromKey, link.inCycle)
        fromNode.stopConsumer(fromKey, channel).await()

        toNode.setLinked(toKey, false)
        toNode.setCycle(toKey, link.inCycle)
        toNode.waitForConsumer(toKey).await()
    }

    suspend fun addAllLinks() {
        network?.getLinks()?.map {
            scope.launch { addLink(it) }
        }?.joinAll()
    }

    suspend fun removeAllLinks() {
        network?.getLinks()?.map {
            scope.launch { removeLink(it) }
        }?.joinAll()
    }

    suspend fun addAllNodes() {
        network?.getNodes()?.map {
            scope.launch { addNode(it) }
        }?.joinAll()
    }

    suspend fun removeAllNodes() {
        val jobs = mutableListOf<Job>()
        val iter = nodes.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            iter.remove()
            Log.d(TAG, "remove node ${entry.key}")
            jobs += scope.launch { entry.value.release() }
        }
        jobs.joinAll()
    }

    override suspend fun release() {
        Log.d(TAG, "release")
        super.release()
        context.release()
    }

    companion object {
        const val TAG = "NetworkExecutor2"
    }

    private fun Node.executor(): NodeExecutor {
        return when (type) {
            NodeType.Camera -> CameraNode(context, properties)
            NodeType.Microphone -> AudioSourceNode(context, properties)
            NodeType.Screen -> SurfaceViewNode(context, properties)
            NodeType.TextureView -> TextureViewNode(context, properties)
            NodeType.GrayscaleFilter -> GrayscaleNode(context, properties)
            NodeType.MediaEncoder -> EncoderNode(context, properties)
            NodeType.RingBuffer -> RingBufferNode(context, properties)
            NodeType.Slice3d -> Slice3dNode(context, properties)
            NodeType.CubeImport -> CubeImportNode(context, properties)
            NodeType.BCubeImport -> BCubeImportNode(context, properties)
            NodeType.Lut3d -> Lut3dNode(context, properties)
            NodeType.RotateMatrix -> RotateMatrixNode(context, properties)
            NodeType.CropResize -> CropResizeNode(context, properties)
            NodeType.CellAuto -> CellularAutoNode(context, properties)
            NodeType.Quantizer -> QuantizerNode(context, properties)
            NodeType.BlurFilter -> BlurNode(context, properties)
            NodeType.Sobel -> SobelNode(context, properties)
            else -> error("check yo nodes")
        }
    }
}
