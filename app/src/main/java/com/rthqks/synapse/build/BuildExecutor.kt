package com.rthqks.synapse.build

import android.view.SurfaceView
import com.rthqks.synapse.data.SeedData
import com.rthqks.flow.exec.ExecutionContext
import com.rthqks.flow.exec.NetworkExecutor
import com.rthqks.flow.exec.node.SurfaceViewNode
import com.rthqks.flow.logic.Link
import com.rthqks.flow.logic.Network
import com.rthqks.flow.logic.NodeDef.Camera
import com.rthqks.flow.logic.NodeDef.Screen
import com.rthqks.flow.logic.PortType
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class BuildExecutor(
    context: ExecutionContext
) : NetworkExecutor(context) {
    private var buildNetwork: Network? = null
    private var cropLink: Link? = null
    private var outputLink: Link? = null
//    private val lutPreviewPool = ConcurrentLinkedQueue<LutPreview>()
//    private val lutPreviews = ConcurrentHashMap<SurfaceTexture, LutPreview>()
//    private val lutJobs = ConcurrentHashMap<SurfaceTexture, Job>()

    suspend fun initialize() = await {
        network = SeedData.BuildNetwork.copy()
        addAllNodes()
        addAllLinks()
    }

    suspend fun setBuildNetwork(network: Network) = await {
        buildNetwork = network
        this.network?.let {
            it += network
        }

        network.getNodes().map { scope.launch { addNode(it) } }.joinAll()
        network.getLinks().map { scope.launch { addLink(it) } }.joinAll()
        network.getPorts().filter { it.exposed && it.input }.forEach {
            Link(ID_CAMERA, Camera.OUTPUT.key, it.nodeId, it.key).also {
                this.network?.addLink(it)
                addLink(it)
            }
        }
    }

    suspend fun setOutputPort(nodeId: Int, portKey: String) = await {
        buildNetwork?.getPort(nodeId, portKey)?.let { port ->
            outputLink?.let {
                this.network?.removeLink(it)
                removeLink(it)
            }
            outputLink = null
            when {
                port.type != PortType.Video -> null
                port.output -> Link(nodeId, portKey, ID_SURFACE_VIEW, Screen.INPUT.key)
                else -> {
                    network?.getLinks(nodeId)?.firstOrNull {
                        it.toNodeId == nodeId && it.toPortId == portKey
                    }?.let {
                        Link(it.fromNodeId, it.fromPortId, ID_SURFACE_VIEW, Screen.INPUT.key)
                    }
                }
            }?.let {
                outputLink = it
                this.network?.addLink(it)
                addLink(it)
            }
        }
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) = await {
        (getNode(ID_SURFACE_VIEW) as? SurfaceViewNode)?.setSurfaceView(surfaceView)
    }

    companion object {
        const val TAG = "BuildExecutor"
        const val ID_CAMERA = 10_001
        const val ID_MIC = 10_002
        const val ID_SURFACE_VIEW = 10_003
        const val ID_CROP = 10_007

    }
}

// ignores node id collisions, must be handled externally
operator fun Network.plusAssign(network: Network) {
    network.getNodes().forEach { addNode(it, it.id) }
    network.getLinks().forEach { addLink(it) }
}

private val Network.videoOut: Pair<Int, String>?
    get() = getPorts().firstOrNull {
        it.exposed && it.output && it.type == com.rthqks.flow.logic.PortType.Video
    }?.let { Pair(it.nodeId, it.key) }

private val Network.videoIn: List<Pair<Int, String>>
    get() = getPorts().filter {
        it.exposed && it.input && it.type == com.rthqks.flow.logic.PortType.Video
    }.map { Pair(it.nodeId, it.key) }
