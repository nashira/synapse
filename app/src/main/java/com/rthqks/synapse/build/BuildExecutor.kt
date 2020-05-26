package com.rthqks.synapse.build

import android.view.SurfaceView
import com.rthqks.synapse.data.SeedData
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NetworkExecutor
import com.rthqks.synapse.exec.node.SurfaceViewNode
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.NodeDef.Camera
import com.rthqks.synapse.logic.NodeDef.Screen
import com.rthqks.synapse.logic.PortType
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class BuildExecutor(
    context: ExecutionContext
) : NetworkExecutor(context) {
    private var buildNetwork: Network? = null
    private var cropLink: Link? = null
    private var outputLink: Link? = null
    private var inputLinks = mutableListOf<Link>()
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
                inputLinks.add(it)
                addLink(it)
            }
        }
    }

    suspend fun setOutputPort(nodeId: Int, portKey: String) = await {
        buildNetwork?.getPort(nodeId, portKey)?.let { port ->
            outputLink?.let { removeLink(it) }
            if (port.output) {
                Link(nodeId, portKey, ID_SURFACE_VIEW, Screen.INPUT.key).also {
                    outputLink = it
                    addLink(it)
                }
            }
        }
    }

    suspend fun setExposed(nodeId: Int, portKey: String) = await {
        buildNetwork?.getPort(nodeId, portKey)?.let { port ->
            if (port.input) {
                Link(ID_CAMERA, Camera.OUTPUT.key, nodeId, portKey).also {
                    inputLinks.add(it)
                    addLink(it)
                }
            }
        }
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) = await {
        (getNode(ID_SURFACE_VIEW) as? SurfaceViewNode)?.setSurfaceView(surfaceView)
    }

    override suspend fun removeAll() {
        await {
            outputLink?.let { removeLink(it) }
            inputLinks.map { scope.launch { removeLink(it) } }.joinAll()
        }
        super.removeAll()
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
        it.exposed && it.output && it.type == PortType.Video
    }?.let { Pair(it.nodeId, it.key) }

private val Network.videoIn: List<Pair<Int, String>>
    get() = getPorts().filter {
        it.exposed && it.input && it.type == PortType.Video
    }.map { Pair(it.nodeId, it.key) }
