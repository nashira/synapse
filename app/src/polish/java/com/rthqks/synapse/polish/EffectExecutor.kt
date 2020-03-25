package com.rthqks.synapse.polish

import android.graphics.SurfaceTexture
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.SurfaceView
import android.view.TextureView
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.NetworkExecutor
import com.rthqks.synapse.exec2.node.*
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class EffectExecutor(context: ExecutionContext) : NetworkExecutor(context) {
    private var effect: Effect? = null
    private val n = Network(0)
    private val camera = n.addNode(NewNode(NodeType.Camera, Effect.ID_CAMERA))
    private val microphone = n.addNode(NewNode(NodeType.Microphone, Effect.ID_MIC))
    private val screen = n.addNode(NewNode(NodeType.Screen, Effect.ID_SURFACE_VIEW))
    private val encoder = n.addNode(NewNode(NodeType.MediaEncoder, Effect.ID_ENCODER))
    private val cube = n.addNode(NewNode(NodeType.BCubeImport, Effect.ID_LUT_IMPORT))
    private val lut = n.addNode(NewNode(NodeType.Lut3d, Effect.ID_LUT))
    private val crop = n.addNode(NewNode(NodeType.CropResize, Effect.ID_THUMBNAIL))
    private var cropLink: Link? = null
    private val lutPreviewPool = ConcurrentLinkedQueue<LutPreview>()
    private val lutPreviews = ConcurrentHashMap<SurfaceTexture, LutPreview>()
    private var runState = NOT_RUNNING

    init {
        microphone.properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        crop.properties[CropSize] = Size(320, 320)

        n.addLinkNoCompute(Link(lut.id, Lut3dNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        n.addLinkNoCompute(
            Link(
                lut.id,
                Lut3dNode.OUTPUT.id,
                encoder.id,
                EncoderNode.INPUT_VIDEO.id
            )
        )
        n.addLinkNoCompute(Link(cube.id, BCubeImportNode.OUTPUT.id, lut.id, Lut3dNode.INPUT_LUT.id))
        n.addLinkNoCompute(
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

        await {
            n.nodes.values.forEach {
                it.properties += context.properties
            }
        }
    }

    suspend fun initializeEffect() = await {
        network = n
        addAllNodes()
        addAllLinks()
    }

    suspend fun swapEffect(effect: Effect) = async {

        this.effect?.network?.let { old ->
            //            val cl = cropLink
//            val links = if (cl != null ) old.getLinks() + cl else old.getLinks()
            val links = old.getLinks()

            links.map { scope.launch { removeLink(it) } }.joinAll()
            old.nodes.map { scope.launch { removeNode(it.value) } }.joinAll()
            links.forEach { n.removeLink(it) }
            old.nodes.forEach { n.removeNode(it.key) }
        }

        val new = effect.network
        new.nodes.forEach { n.addNode(it.value) }
        new.getLinks().forEach { n.addLinkNoCompute(it) }
        n.computeComponents()
        new.nodes.map { scope.launch { addNode(it.value) } }.joinAll()
        new.getLinks().map { scope.launch { addLink(it) } }.joinAll()

        (getNode(Effect.ID_LUT) as? Lut3dNode)?.resetLutMatrix()
        this.effect = effect
    }

//    override suspend fun release() {
//        await {
//            removeAllLinks()
//            removeAllNodes()
//        }
//        super.release()
//    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) = await {
        (getNode(Effect.ID_SURFACE_VIEW) as? SurfaceViewNode)?.setSurfaceView(surfaceView)
    }

    suspend fun startLutPreview() = await {
        n.getLinks(Effect.ID_LUT).firstOrNull {
            it.toNodeId == Effect.ID_LUT && it.toPortId == Lut3dNode.INPUT.id
        }?.let {
            val cl =
                Link(it.fromNodeId, it.fromPortId, Effect.ID_THUMBNAIL, CropResizeNode.INPUT.id)
            addLink(cl)
            cropLink = cl
        }
    }

    suspend fun stopLutPreview() = await {
        cropLink?.let {
            removeLink(it)
            cropLink = null
        }
    }

    suspend fun registerLutPreview(textureView: TextureView, lut: String) {
//        Log.d(TAG, "register $lut ${textureView.surfaceTexture}")
        if (lutPreviews.containsKey(textureView.surfaceTexture)) {
            Log.e(TAG, "lut already being previewed")
            return
        } else if (textureView.surfaceTexture == null) {
            Log.e(TAG, "surfaceTexture is null")
        }


        val preview = lutPreviewPool.poll() ?: run {
            //        val preview = null ?: run {
//            Log.d(TAG, "creating new lut preview")
            val p = LutPreview()
            p.setup()
            p
        }

        lutPreviews[textureView.surfaceTexture] = preview
        updateCubeUri(preview.cube, lut)
        (getNode(preview.screen.id) as TextureViewNode).setTextureView(textureView)
    }

    suspend fun unregisterLutPreview(surfaceTexture: SurfaceTexture) {
//        Log.d(TAG, "unregister $surfaceTexture")
        lutPreviews.remove(surfaceTexture)?.let {
            //            Log.d(TAG, "removed $surfaceTexture")
//            it.release()
            (getNode(it.screen.id) as TextureViewNode).removeTextureView()
            lutPreviewPool += it
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

    companion object {
        const val TAG = "EffectExecutor"
        const val NOT_RUNNING = 0
        const val RUNNING = 1
    }

    private inner class LutPreview {
        val cube = n.addNode(NewNode(NodeType.BCubeImport))
        val lut = n.addNode(NewNode(NodeType.Lut3d))
        val screen = n.addNode(NewNode(NodeType.TextureView))

        val l2s = Link(lut.id, Lut3dNode.OUTPUT.id, screen.id, TextureViewNode.INPUT.id)
        val cu2l = Link(cube.id, BCubeImportNode.OUTPUT.id, lut.id, Lut3dNode.INPUT_LUT.id)
        val cr2l = Link(crop.id, CropResizeNode.OUTPUT.id, lut.id, Lut3dNode.INPUT.id)

        init {
            n.addLinkNoCompute(l2s)
            n.addLinkNoCompute(cu2l)
            n.addLinkNoCompute(cr2l)
        }

        suspend fun setup() {
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
