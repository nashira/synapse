package com.rthqks.synapse.polish

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
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Network
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.logic.NodeDef
import com.rthqks.synapse.logic.NodeDef.BCubeImport.LutUri
import com.rthqks.synapse.logic.NodeDef.CropResize.CropSize
import com.rthqks.synapse.logic.NodeDef.Microphone.AudioSource
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class EffectExecutor(context: ExecutionContext) : NetworkExecutor(context) {
    private var effect: Effect? = null
    private val n = Network(0)
    private val microphone = n.addNode(NodeDef.Microphone.toNode(Effect.ID_MIC))
    private val screen = n.addNode(NodeDef.Screen.toNode(Effect.ID_SURFACE_VIEW))
    private val encoder = n.addNode(NodeDef.MediaEncoder.toNode(Effect.ID_ENCODER))
    private val cube = n.addNode(NodeDef.BCubeImport.toNode(Effect.ID_LUT_IMPORT))
    private val lut = n.addNode(NodeDef.Lut3d.toNode(Effect.ID_LUT))
    private val crop = n.addNode(NodeDef.CropResize.toNode(Effect.ID_THUMBNAIL))
    private var cropLink: Link? = null
    private val lutPreviewPool = ConcurrentLinkedQueue<LutPreview>()
    private val lutPreviews = ConcurrentHashMap<SurfaceTexture, LutPreview>()
    private val lutJobs = ConcurrentHashMap<SurfaceTexture, Job>()

    init {
        microphone.properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        crop.properties[CropSize] = Size(320, 320)

        n.addLink(Link(lut.id, Lut3dNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        n.addLink(
            Link(
                lut.id,
                Lut3dNode.OUTPUT.id,
                encoder.id,
                EncoderNode.INPUT_VIDEO.id
            )
        )
        n.addLink(Link(cube.id, BCubeImportNode.OUTPUT.id, lut.id, Lut3dNode.INPUT_LUT.id))
        n.addLink(
            Link(
                microphone.id,
                AudioSourceNode.OUTPUT.id,
                encoder.id,
                EncoderNode.INPUT_AUDIO.id
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

    suspend fun swapEffect(effect: Effect) {
        val isLutPreview = cropLink != null
        stopLutPreview()
        await {

            val newCam = effect.network.nodes.values.firstOrNull { it.type == NodeDef.Camera.key }
            val oldCam =
                this.effect?.network?.nodes?.values?.firstOrNull { it.type == NodeDef.Camera.key }
            var camNode: CameraNode? = null

            this.effect?.let { oldEff ->
                val old = oldEff.network
                val links = old.getLinks() + Link(
                    oldEff.videoOut.first, oldEff.videoOut.second,
                    Effect.ID_LUT, Lut3dNode.INPUT.id
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

            newCam?.properties?.plusAssign(context.properties)

            val new = effect.network
            val newLinks = new.getLinks() + Link(
                effect.videoOut.first, effect.videoOut.second,
                Effect.ID_LUT, Lut3dNode.INPUT.id
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

            (getNode(Effect.ID_LUT) as? Lut3dNode)?.resetLutMatrix()
            this.effect = effect
        }

        if (isLutPreview) {
            startLutPreview()
        }
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) = await {
        (getNode(Effect.ID_SURFACE_VIEW) as? SurfaceViewNode)?.setSurfaceView(surfaceView)
    }

    suspend fun startLutPreview() = await {
        effect?.let {
            cropLink = Link(
                it.videoOut.first,
                it.videoOut.second,
                Effect.ID_THUMBNAIL,
                CropResizeNode.INPUT.id
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

    companion object {
        const val TAG = "EffectExecutor"
    }

    private inner class LutPreview {
        val cube = NodeDef.BCubeImport.toNode()
        val lut = NodeDef.Lut3d.toNode()
        val screen = NodeDef.TextureView.toNode()

        lateinit var l2s: Link
        lateinit var cu2l: Link
        lateinit var cr2l: Link

        suspend fun setup() {
            n.addNode(cube)
            n.addNode(lut)
            n.addNode(screen)
            l2s = Link(lut.id, Lut3dNode.OUTPUT.id, screen.id, TextureViewNode.INPUT.id)
            cu2l = Link(cube.id, BCubeImportNode.OUTPUT.id, lut.id, Lut3dNode.INPUT_LUT.id)
            cr2l = Link(crop.id, CropResizeNode.OUTPUT.id, lut.id, Lut3dNode.INPUT.id)
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
}
