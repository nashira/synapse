package com.rthqks.synapse.effect

import android.graphics.SurfaceTexture
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.view.TextureView
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.NetworkExecutor
import com.rthqks.synapse.exec.node.*
import com.rthqks.synapse.logic.*
import com.rthqks.synapse.logic.NodeDef.*
import com.rthqks.synapse.logic.NodeDef.BCubeImport.LutUri
import com.rthqks.synapse.logic.NodeDef.CropResize.CropSize
import com.rthqks.synapse.logic.NodeDef.Microphone.AudioSource
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class EffectExecutor(context: ExecutionContext) : NetworkExecutor(context) {
    private var effect: Network? = null
    private val n = Network(0, "effect_common")
    private val microphone = n.addNode(Microphone.toNode(ID_MIC))
    private val screen = n.addNode(Screen.toNode(ID_SURFACE_VIEW))
    private val encoder = n.addNode(MediaEncoder.toNode(ID_ENCODER))
    private val cube = n.addNode(BCubeImport.toNode(ID_LUT_IMPORT))
    private val lut = n.addNode(Lut3d.toNode(ID_LUT))
    private val crop = n.addNode(CropResize.toNode(ID_THUMBNAIL))
    private var cropLink: Link? = null
    private val lutPreviewPool = ConcurrentLinkedQueue<LutPreview>()
    private val lutPreviews = ConcurrentHashMap<SurfaceTexture, LutPreview>()
    private val lutJobs = ConcurrentHashMap<SurfaceTexture, Job>()

    init {
        microphone.properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        crop.properties[CropSize] = Size(320, 320)

        n.addLink(Link(lut.id, Lut3d.OUTPUT.key, screen.id, Screen.INPUT.key))
        n.addLink(
            Link(
                lut.id,
                Lut3d.OUTPUT.key,
                encoder.id,
                MediaEncoder.VIDEO_IN.key
            )
        )
        n.addLink(Link(cube.id, BCubeImport.OUTPUT.key, lut.id, Lut3d.LUT_IN.key))
        n.addLink(
            Link(
                microphone.id,
                Microphone.OUTPUT.key,
                encoder.id,
                MediaEncoder.AUDIO_IN.key
            )
        )
    }

    override suspend fun setup() {
        super.setup()

        n.nodes.values.forEach {
            it.properties += context.properties
        }
    }

    suspend fun initializeEffect() = await {
        network = n
        addAllNodes()
        addAllLinks()
    }

    suspend fun swapEffect(effect: Network) {
        val isLutPreview = cropLink != null
        stopLutPreview()
        await {

            val newCam = effect.nodes.values.firstOrNull { it.type == Camera.key }
            val oldCam =
                this.effect?.nodes?.values?.firstOrNull { it.type == Camera.key }
            var camNode: CameraNode? = null

            this.effect?.let { oldEff ->
                val old = oldEff
                val links = old.getLinks() + Link(
                    oldEff.videoOut.first, oldEff.videoOut.second,
                    ID_LUT, Lut3d.SOURCE_IN.key
                )

                links.map { scope.launch { removeLink(it) } }.joinAll()
                old.nodes.map {
                    scope.launch {
                        // keep the old CameraNode if new effect also has CameraNode
                        if (it.key == oldCam?.id && newCam != null) {
                            camNode = nodes.remove(it.key) as CameraNode?
                        } else {
                            removeNode(it.value)
                        }
                    }
                }.joinAll()
                links.forEach { n.removeLink(it) }
                old.nodes.forEach { n.removeNode(it.key) }
            }

            listOf(Camera.CameraFacing, Camera.FrameRate, Camera.Stabilize, Camera.VideoSize).forEach {
                newCam?.properties?.put(context.properties.getProperty(it)!!)
            }

            val new = effect
            val newLinks = new.getLinks() + Link(
                effect.videoOut.first, effect.videoOut.second,
                ID_LUT, Lut3d.SOURCE_IN.key
            )
            new.nodes.forEach { n.addNode(it.value) }
            newLinks.forEach { n.addLink(it) }
            n.computeComponents()
            new.nodes.map {
                scope.launch {
                    // reuse the old CameraNode if available
                    if (it.key == newCam?.id && camNode != null) {
                        camNode?.setProperties(it.value.properties)
                        nodes[it.key] = camNode!!
                    } else {
                        addNode(it.value)
                    }
                }
            }.joinAll()
            newLinks.map { scope.launch { addLink(it) } }.joinAll()

            (getNode(ID_LUT) as? Lut3dNode)?.resetLutMatrix()
            this.effect = effect
        }

        if (isLutPreview) {
            startLutPreview()
        }
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) = await {
        (getNode(ID_SURFACE_VIEW) as? SurfaceViewNode)?.setSurfaceView(surfaceView)
    }

    suspend fun startLutPreview() = await {
        effect?.let {
            cropLink = Link(
                it.videoOut.first,
                it.videoOut.second,
                ID_THUMBNAIL,
                CropResize.INPUT.key
            ).also { link ->
                addLink(link)
            }
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
        val surfaceTexture = textureView.surfaceTexture
        lutJobs[surfaceTexture] = scope.launch {
            val preview = lutPreviewPool.poll() ?: await {
//            Log.d(TAG, "creating new lut preview")
                val p = LutPreview()
                p.setup()
                p
            }

            if (isActive) {
                lutPreviews[surfaceTexture] = preview
                updateCubeUri(preview.cube, lut)
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

    private suspend fun updateCubeUri(node: Node, lut: String) {
        val uri = Uri.parse("assets:///cube/$lut.bcube")
        node.properties[LutUri] = uri
        (getNode(node.id) as? BCubeImportNode)?.let {
            it.loadCubeFile()
            it.sendMessage()
        }
    }

    suspend fun setLut(lut: String) = await {
        updateCubeUri(cube, lut)
    }

    suspend fun removeAll() = await {
        removeAllLinks()
        removeAllNodes()
    }

    private inner class LutPreview {
        val cube = BCubeImport.toNode()
        val lut = Lut3d.toNode()
        val screen = NodeDef.TextureView.toNode()

        lateinit var l2s: Link
        lateinit var cu2l: Link
        lateinit var cr2l: Link

        suspend fun setup() {
            n.addNode(cube)
            n.addNode(lut)
            n.addNode(screen)
            l2s = Link(lut.id, Lut3d.OUTPUT.key, screen.id, NodeDef.TextureView.INPUT.key)
            cu2l = Link(cube.id, BCubeImport.OUTPUT.key, lut.id, Lut3d.LUT_IN.key)
            cr2l = Link(crop.id, CropResize.OUTPUT.key, lut.id, Lut3d.SOURCE_IN.key)
            n.addLink(l2s)
            n.addLink(cu2l)
            n.addLink(cr2l)

            listOf(cube, lut, screen).map { scope.launch { addNode(it) } }.joinAll()
            listOf(l2s, cu2l, cr2l).map { scope.launch { addLink(it) } }.joinAll()
        }

        suspend fun release() {
            n.removeLink(l2s)
            n.removeLink(cu2l)
            n.removeLink(cr2l)
            n.removeNode(cube.id)
            n.removeNode(lut.id)
            n.removeNode(screen.id)

            listOf(l2s, cu2l, cr2l).map { scope.launch { removeLink(it) } }.joinAll()
            listOf(cube, lut, screen).map { scope.launch { removeNode(it) } }.joinAll()
        }
    }

    companion object {
        const val TAG = "EffectExecutor"

        // node id allocations
        // 0 - 10,000 : effect specific nodes
        // 10000+ : effect context nodes
        const val ID_MIC = 10_002
        const val ID_SURFACE_VIEW = 10_003
        const val ID_ENCODER = 10_004
        const val ID_LUT_IMPORT = 10_005
        const val ID_LUT = 10_006
        const val ID_THUMBNAIL = 10_007

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

private val Network.videoOut: Pair<Int, String>
    get() {
        ports.forEach { entry ->
            entry.value.forEach {
                if (it.output && it.type == PortType.Video) {
                    return Pair(entry.key, it.id)
                }
            }
        }
        error("missing exposed video port")
    }

//    private val propertyTypes = mutableMapOf<Property.Key<*>, PropertyHolder<Any?>>()
//    val properties = Properties()
//
//    fun <T> addProperty(property: Property<T>, holder: PropertyHolder<T>) {
//        properties.put(property)
//        propertyTypes[property.key] = holder as PropertyHolder<Any?>
//    }

