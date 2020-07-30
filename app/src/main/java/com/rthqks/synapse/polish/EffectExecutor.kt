package com.rthqks.synapse.polish

import android.graphics.SurfaceTexture
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import com.rthqks.flow.exec.ExecutionContext
import com.rthqks.flow.exec.NetworkExecutor
import com.rthqks.flow.exec.node.BCubeImportNode
import com.rthqks.flow.exec.node.Lut3dNode
import com.rthqks.flow.exec.node.SurfaceViewNode
import com.rthqks.flow.exec.node.TextureViewNode
import com.rthqks.flow.logic.*
import com.rthqks.flow.logic.NodeDef.*
import com.rthqks.flow.logic.NodeDef.BCubeImport.LutUri
import com.rthqks.flow.logic.NodeDef.Lut3d.LutStrength
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class EffectExecutor(
    context: ExecutionContext
) : NetworkExecutor(context) {
    private lateinit var baseNetwork: Network
    private var effect: Network? = null
    private var cropLink: Link? = null
    private val lutPreviewPool = ConcurrentLinkedQueue<LutPreview>()
    private val lutPreviews = ConcurrentHashMap<SurfaceTexture, LutPreview>()
    private val lutJobs = ConcurrentHashMap<SurfaceTexture, Job>()

    suspend fun setBaseNetwork(network: Network) = await {
        baseNetwork = network
        this.network = network
    }

    suspend fun initializeEffect() = await {
        addAllNodes()
        addAllLinks()
    }

    suspend fun swapEffect(effect: Network) {
        Log.d(TAG, "swapEffect from ${this.effect?.id} to ${effect.id}")
        val isLutPreview = cropLink != null
        stopLutPreview()

        await {
            val videoIn = effect.videoIn
            val videoOut = effect.videoOut

            if (isResumed &&
                (videoIn.isNotEmpty() || (videoIn.isEmpty() && videoOut == null))
            ) {
                getNode(ID_CAMERA)?.resume()
            } else {
                getNode(ID_CAMERA)?.pause()
            }

            removeOldEffect()

            addNewEffect(effect, videoIn, videoOut)

            (getNode(ID_LUT) as? Lut3dNode)?.resetLutMatrix()
            this.effect = effect
        }

        if (isLutPreview) {
            startLutPreview()
        }
    }

    private suspend fun addNewEffect(
        effect: Network,
        videoIn: List<Pair<Int, String>>,
        videoOut: Pair<Int, String>?
    ) {
        val new = effect
        val newLinks = mutableListOf<Link>()
        newLinks += new.getLinks()
        videoOut?.let {
            newLinks += Link(
                it.first, it.second,
                ID_LUT, Lut3d.SOURCE_IN.key
            )
        }
        videoIn.forEach {
            newLinks += Link(
                ID_CAMERA, Camera.OUTPUT.key,
                it.first, it.second
            )
        }

        // `none` effect
        if (videoIn.isEmpty() && videoOut == null) {
            newLinks += Link(
                ID_CAMERA, Camera.OUTPUT.key,
                ID_LUT, Lut3d.SOURCE_IN.key
            )
        }

        val newNodes = new.getNodes().map { baseNetwork.addNode(it, it.id) }
        baseNetwork.addLinks(newLinks)
        newNodes.map {
            scope.launch {
                addNode(it)
            }
        }.joinAll()
        newLinks.map { scope.launch { addLink(it) } }.joinAll()
    }

    private suspend fun removeOldEffect() {
        this.effect?.let { oldEff ->
            val old = oldEff
            val links = mutableListOf<Link>()
            links += old.getLinks()
            val videoOut = oldEff.videoOut
            videoOut?.let {
                links += Link(
                    it.first, it.second,
                    ID_LUT, Lut3d.SOURCE_IN.key
                )
            }
            val videoIn = oldEff.videoIn
            videoIn.map {
                links += Link(
                    ID_CAMERA, Camera.OUTPUT.key,
                    it.first, it.second
                )
            }

            // `none` effect
            if (videoIn.isEmpty() && videoOut == null) {
                links += Link(
                    ID_CAMERA, Camera.OUTPUT.key,
                    ID_LUT, Lut3d.SOURCE_IN.key
                )
            }

            links.map { scope.launch { removeLink(it) } }.joinAll()
            old.getNodes().map {
                scope.launch { removeNode(it) }
            }.joinAll()

            links.forEach { baseNetwork.removeLink(it) }
            old.getNodes().forEach { baseNetwork.removeNode(it.id) }
        }
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) = await {
        (getNode(ID_SURFACE_VIEW) as? SurfaceViewNode)?.setSurfaceView(surfaceView)
    }

    suspend fun startLutPreview() = await {
        effect ?: return@await
        val videoOut = effect?.videoOut ?: Pair(ID_CAMERA, Camera.OUTPUT.key)
        cropLink = Link(
            videoOut.first,
            videoOut.second,
            ID_CROP,
            CropResize.INPUT.key
        ).also { link ->
            addLink(link)
        }
    }

    suspend fun stopLutPreview() = await {
        cropLink?.let {
            removeLink(it)
            cropLink = null
        }
    }

    fun registerLutPreview(textureView: TextureView, lut: String) {
//        Log.d(TAG, "register $lut ${textureView.surfaceTexture}")
        val surfaceTexture = textureView.surfaceTexture ?: return
        lutJobs[surfaceTexture] = scope.launch {
            val preview = lutPreviewPool.poll() ?: await {
//            Log.d(TAG, "creating new lut preview")
                val p = LutPreview()
                p.setup()
                p
            }

            if (isActive) {
                lutPreviews[surfaceTexture] = preview
                updateCubeUri(preview.cube.id, lut)
                (getNode(preview.screen.id) as TextureViewNode).setTextureView(textureView)
            } else {
                lutPreviewPool += preview
            }

            this.coroutineContext[Job]?.invokeOnCompletion {
                if (it != null && it !is CancellationException) {
                    Log.w(TAG, "job was interrupted")
                }
            }
        }
    }

    fun unregisterLutPreview(surfaceTexture: SurfaceTexture) {
//        Log.d(TAG, "unregister $surfaceTexture")
        scope.launch {
            lutJobs.remove(surfaceTexture)?.cancelAndJoin()
            lutPreviews.remove(surfaceTexture)?.let {
                //            Log.d(TAG, "removed $surfaceTexture")
//            it.release()

                (getNode(it.screen.id) as? TextureViewNode)?.removeTextureView()
                surfaceTexture.release()
                lutPreviewPool += it
            }
        }
    }

    private suspend fun updateCubeUri(nodeId: Int, lut: String) {
        val uri = Uri.parse("assets:///cube/$lut.bcube")
        baseNetwork.setProperty(nodeId, LutUri, uri)
        (getNode(nodeId) as? BCubeImportNode)?.let {
            it.loadCubeFile()
            it.sendMessage()
        }
    }

    suspend fun setLut(lut: String) = await {
        updateCubeUri(ID_LUT_IMPORT, lut)
    }

    fun getLutStrength(): Float = effect?.let {
        baseNetwork.getPropertyValue(ID_LUT, LutStrength)
    } ?: 1f

    fun getProperty(nodeId: Int, key: String) = baseNetwork.getProperty(nodeId, key)

    fun <T : Any> setProperty(nodeId: Int, key: Property.Key<T>, value: T) =
        network?.setProperty(nodeId, key, value)

    private inner class LutPreview {
        lateinit var cube: Node
        lateinit var lut: Node
        lateinit var screen: Node

        lateinit var l2s: Link
        lateinit var cu2l: Link
        lateinit var cr2l: Link

        suspend fun setup() {
            cube = baseNetwork.addNode(BCubeImport)
            lut = baseNetwork.addNode(Lut3d)
            screen = baseNetwork.addNode(NodeDef.TextureView)
            l2s = Link(lut.id, Lut3d.OUTPUT.key, screen.id, NodeDef.TextureView.INPUT.key)
            cu2l = Link(cube.id, BCubeImport.OUTPUT.key, lut.id, Lut3d.LUT_IN.key)
            cr2l = Link(ID_CROP, CropResize.OUTPUT.key, lut.id, Lut3d.SOURCE_IN.key)
            baseNetwork.addLink(l2s)
            baseNetwork.addLink(cu2l)
            baseNetwork.addLink(cr2l)

            listOf(cube, lut, screen).map { scope.launch { addNode(it) } }.joinAll()
            listOf(l2s, cu2l, cr2l).map { scope.launch { addLink(it) } }.joinAll()
        }

        suspend fun release() {
            baseNetwork.removeLink(l2s)
            baseNetwork.removeLink(cu2l)
            baseNetwork.removeLink(cr2l)
            baseNetwork.removeNode(cube.id)
            baseNetwork.removeNode(lut.id)
            baseNetwork.removeNode(screen.id)

            listOf(l2s, cu2l, cr2l).map { scope.launch { removeLink(it) } }.joinAll()
            listOf(cube, lut, screen).map { scope.launch { removeNode(it) } }.joinAll()
        }
    }

    companion object {
        const val TAG = "EffectExecutor"

        // node id allocations
        // 0 - 10,000 : effect specific nodes
        // 10000+ : effect context nodes
        const val ID_CAMERA = 10_001
        const val ID_MIC = 10_002
        const val ID_SURFACE_VIEW = 10_003
        const val ID_ENCODER = 10_004
        const val ID_LUT_IMPORT = 10_005
        const val ID_LUT = 10_006
        const val ID_CROP = 10_007

        val LUTS = listOf(
            "identity",
            "invert",
            "yellow_film_01",
            "action_magenta_01",
            "action_red_01",
            "adventure_1453",
            "agressive_highlights",
            "bleech_bypass_green",
            "bleech_bypass_yellow_01",
            "blue_dark",
            "bright_green_01",
            "brownish",
            "colorful_0209",
            "conflict_01",
            "contrast_highlights",
            "contrasty_afternoon",
            "cross_process_cp3",
            "cross_process_cp4",
            "cross_process_cp6",
            "cross_process_cp14",
            "cross_process_cp15",
            "cross_process_cp16",
            "cross_process_cp18",
            "cross_process_cp130",
            "dark_green_02",
            "dark_green",
            "dark_place_01",
            "dream_1",
            "dream_85",
            "faded_retro_01",
            "faded_retro_02",
            "film_0987",
            "film_9879",
            "film_high_contrast",
            "flat_30",
            "green_2025",
            "green_action",
            "green_afternoon",
            "green_conflict",
            "green_day_01",
            "green_day_02",
            "green_g09",
            "green_indoor",
            "green_light",
            "harsh_day",
            "harsh_sunset",
            "highlights_protection",
            "indoor_blue",
            "low_contrast_blue",
            "low_key_01",
            "magenta_day_01",
            "magenta_day",
            "magenta_dream",
            "memories",
            "moonlight_01",
            "mostly_blue",
            "muted_01",
            "only_red_and_blue",
            "only_red",
            "operation_yellow",
            "orange_dark_4",
            "orange_dark_7",
            "orange_dark_look",
            "orange_underexposed",
            "protect_highlights_01",
            "red_afternoon_01",
            "red_day_01",
            "red_dream_01",
            "retro_brown_01",
            "retro_magenta_01",
            "retro_yellow_01",
            "smart_contrast",
            "subtle_blue",
            "subtle_green",
            "yellow_55b"
        )
    }
}

private val Network.videoOut: Pair<Int, String>?
    get() = getPorts().firstOrNull {
        it.exposed && it.output && it.type == com.rthqks.flow.logic.PortType.Video
    }?.let { Pair(it.nodeId, it.key) }

private val Network.videoIn: List<Pair<Int, String>>
    get() = getPorts().filter {
        it.exposed && it.input && it.type == com.rthqks.flow.logic.PortType.Video
    }.map { Pair(it.nodeId, it.key) }
