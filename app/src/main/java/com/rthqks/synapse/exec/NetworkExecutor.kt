package com.rthqks.synapse.exec

import android.util.Log
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.NodeDef
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

open class NetworkExecutor(context: ExecutionContext) : Executor(context) {
    protected val nodes = ConcurrentHashMap<Int, NodeExecutor>()
    var network: Network? = null
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

    protected fun getNode(id: Int) = nodes[id]

    suspend fun addNode(node: Node) {
        Log.d(TAG, "add node ${node.id}")
        val executor = executor(node.type, context, Properties(node.properties))
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
        executor.setup()
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

        val channel = fromNode.getConsumer(fromKey)
        toNode.startConsumer(toKey, channel as ReceiveChannel<Message<Any?>>)
    }

    suspend fun removeLink(link: Link) {
        val fromNode = nodes[link.fromNodeId] ?: error("missing node ${link.fromNodeId}")
        val toNode = nodes[link.toNodeId] ?: error("missing node ${link.toNodeId}")
        val fromKey = Connection.Key<Any?>(link.fromPortId)
        val toKey = Connection.Key<Any?>(link.toPortId)

        val channel = toNode.channel(toKey)!!

        Log.d(TAG, "remove link $link")

        fromNode.setCycle(fromKey, link.inCycle)
        fromNode.stopConsumer(fromKey, channel)

        toNode.setLinked(toKey, false)
        toNode.setCycle(toKey, link.inCycle)
        toNode.waitForConsumer(toKey)
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

    suspend fun removeAll() = await {
        removeAllLinks()
        removeAllNodes()
    }

    companion object {
        const val TAG = "NetworkExecutor"
        private val EXECUTORS =
            mutableMapOf<String, (ExecutionContext, Properties) -> NodeExecutor>()

        fun executor(key: String, context: ExecutionContext, properties: Properties) =
            EXECUTORS[key]?.invoke(context, properties) ?: error("missing executor for $key")

        init {
            EXECUTORS[NodeDef.Camera.key] =
                { context, properties -> CameraNode(context, properties) }
            EXECUTORS[NodeDef.Microphone.key] =
                { context, properties -> AudioSourceNode(context, properties) }
            EXECUTORS[NodeDef.Screen.key] =
                { context, properties -> SurfaceViewNode(context, properties) }
            EXECUTORS[NodeDef.TextureView.key] =
                { context, properties -> TextureViewNode(context, properties) }
            EXECUTORS[NodeDef.MediaEncoder.key] =
                { context, properties -> EncoderNode(context, properties) }
            EXECUTORS[NodeDef.RingBuffer.key] =
                { context, properties -> RingBufferNode(context, properties) }
            EXECUTORS[NodeDef.Slice3d.key] =
                { context, properties -> Slice3dNode(context, properties) }
            EXECUTORS[NodeDef.CubeImport.key] =
                { context, properties -> CubeImportNode(context, properties) }
            EXECUTORS[NodeDef.BCubeImport.key] =
                { context, properties -> BCubeImportNode(context, properties) }
            EXECUTORS[NodeDef.Lut3d.key] = { context, properties -> Lut3dNode(context, properties) }
            EXECUTORS[NodeDef.RotateMatrix.key] =
                { context, properties -> RotateMatrixNode(context, properties) }
            EXECUTORS[NodeDef.CellAuto.key] =
                { context, properties -> CellularAutoNode(context, properties) }
            EXECUTORS[NodeDef.Quantizer.key] =
                { context, properties -> QuantizerNode(context, properties) }
            EXECUTORS[NodeDef.CropGrayBlur.key] =
                { context, properties -> CropGrayBlurNode(context, properties) }
            EXECUTORS[NodeDef.CropResize.key] =
                { context, properties -> CropResizeNode(context, properties) }
            EXECUTORS[NodeDef.Sobel.key] = { context, properties -> SobelNode(context, properties) }
            EXECUTORS[NodeDef.ImageBlend.key] =
                { context, properties -> ImageBlendNode(context, properties) }
        }
    }
}
