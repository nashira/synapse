package com.rthqks.synapse.polish

import android.media.MediaRecorder
import android.net.Uri
import android.view.SurfaceView
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.NetworkExecutor
import com.rthqks.synapse.exec2.node.*
import com.rthqks.synapse.logic.*
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

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

    init {
        microphone.properties[AudioSource] = MediaRecorder.AudioSource.CAMCORDER
        cube.properties[LutUri] = Uri.parse("assets:///cube/identity.bcube")

        n.addLinkNoCompute(Link(lut.id, Lut3dNode.OUTPUT.id, screen.id, SurfaceViewNode.INPUT.id))
        n.addLinkNoCompute(Link(lut.id, Lut3dNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_VIDEO.id))
        n.addLinkNoCompute(Link(cube.id, BCubeImportNode.OUTPUT.id, lut.id, Lut3dNode.INPUT_LUT.id))
        n.addLinkNoCompute(Link(microphone.id, AudioSourceNode.OUTPUT.id, encoder.id, EncoderNode.INPUT_AUDIO.id))
    }

    override suspend fun setup() {
        super.setup()

        n.nodes.values.forEach {
            it.properties += context.properties
        }

        network = n
        addAllNodes()
        addAllLinks()
    }

    suspend fun swapEffect(effect: Effect) = async {
        val old = this.effect?.network
        this.effect = effect
        val new = effect.network

        old?.getLinks()?.map { scope.launch { removeLink(it) } }?.joinAll()
        old?.nodes?.map { scope.launch { removeNode(it.value) } }?.joinAll()
        old?.getLinks()?.forEach {n.removeLink(it) }
        old?.nodes?.forEach { n.removeNode(it.key) }

        new.nodes.forEach { n.addNode(it.value) }
        new.getLinks().forEach { n.addLinkNoCompute(it) }
        n.computeComponents()
        new.nodes.map { scope.launch { addNode(it.value) } }.joinAll()
        new.getLinks().map { scope.launch { addLink(it) } }.joinAll()

        (getNode(Effect.ID_LUT) as? Lut3dNode)?.resetLutMatrix()
    }

//    override suspend fun release() {
//        await {
//            removeAllLinks()
//            removeAllNodes()
//        }
//        super.release()
//    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) {
        (getNode(Effect.ID_SURFACE_VIEW) as? SurfaceViewNode)?.setSurfaceView(surfaceView)
    }

    companion object {
        const val TAG = "EffectExecutor"
        val STABLE_IDS = mutableSetOf(
            Effect.ID_CAMERA,
            Effect.ID_MIC,
            Effect.ID_ENCODER,
            Effect.ID_SURFACE_VIEW,
            Effect.ID_LUT,
            Effect.ID_LUT_IMPORT
        )
        val STABLE_LINKS = listOf(
            Link(
                Effect.ID_MIC,
                AudioSourceNode.OUTPUT.id,
                Effect.ID_ENCODER,
                EncoderNode.INPUT_AUDIO.id
            ),
            Link(
                Effect.ID_LUT_IMPORT,
                BCubeImportNode.OUTPUT.id,
                Effect.ID_LUT,
                Lut3dNode.INPUT_LUT.id
            ),
            Link(Effect.ID_LUT, Lut3dNode.OUTPUT.id, Effect.ID_SURFACE_VIEW, SurfaceViewNode.INPUT.id),
            Link(Effect.ID_LUT, Lut3dNode.OUTPUT.id, Effect.ID_ENCODER, EncoderNode.INPUT_VIDEO.id)
        )
    }
}
