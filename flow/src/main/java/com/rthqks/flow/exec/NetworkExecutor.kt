package com.rthqks.flow.exec

import android.util.Log
import com.rthqks.flow.exec.node.*
import com.rthqks.flow.logic.Link
import com.rthqks.flow.logic.Network
import com.rthqks.flow.logic.Node
import com.rthqks.flow.logic.NodeDef
import com.rthqks.flow.logic.NodeDef.*
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
            scope.launch {
                Log.d(TAG, "resuming: ${it.key}")
                it.value.resume()
                Log.d(TAG, "resumed: ${it.key}")
            }
        }.joinAll()
    }

    protected fun getNode(id: Int) = nodes[id]

    suspend fun addNode(node: Node) {
        Log.d(TAG, "add node ${node.id}")
        val executor = executor(node.type, context, Properties(node.properties))
        nodes[node.id] = executor
//        network?.getLinks(node.id)?.forEach {
//            val key = if (it.fromNodeId == node.id) {
//                Connection.Key<Any?>(it.fromPortId)
//            } else {
//                Connection.Key(it.toPortId)
//            }
//            executor.setLinked(key)
//            if (it.inCycle) {
//                executor.setCycle(key)
//            }
//        }
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

        fromNode.setLinked(fromKey)
        toNode.setLinked(toKey)

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

    open suspend fun removeAll() = await {
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
            EXECUTORS[Microphone.key] =
                { context, properties -> AudioSourceNode(context, properties) }
            EXECUTORS[Screen.key] =
                { context, properties -> SurfaceViewNode(context, properties) }
            EXECUTORS[TextureView.key] =
                { context, properties -> TextureViewNode(context, properties) }
            EXECUTORS[MediaEncoder.key] =
                { context, properties -> EncoderNode(context, properties) }
            EXECUTORS[RingBuffer.key] =
                { context, properties -> RingBufferNode(context, properties) }
            EXECUTORS[Slice3d.key] =
                { context, properties -> Slice3dNode(context, properties) }
            EXECUTORS[CubeImport.key] =
                { context, properties -> CubeImportNode(context, properties) }
            EXECUTORS[BCubeImport.key] =
                { context, properties -> BCubeImportNode(context, properties) }
            EXECUTORS[Lut3d.key] = { context, properties -> Lut3dNode(context, properties) }
            EXECUTORS[RotateMatrix.key] =
                { context, properties -> RotateMatrixNode(context, properties) }
            EXECUTORS[CellAuto.key] =
                { context, properties -> CellularAutoNode(context, properties) }
            EXECUTORS[Quantizer.key] =
                { context, properties -> QuantizerNode(context, properties) }
            EXECUTORS[CropGrayBlur.key] =
                { context, properties -> CropGrayBlurNode(context, properties) }
            EXECUTORS[CropResize.key] =
                { context, properties -> CropResizeNode(context, properties) }
            EXECUTORS[Sobel.key] = { context, properties -> SobelNode(context, properties) }
            EXECUTORS[ImageBlend.key] =
                { context, properties -> ImageBlendNode(context, properties) }
        }
    }
}
