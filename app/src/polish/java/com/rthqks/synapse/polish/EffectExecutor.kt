package com.rthqks.synapse.polish

import android.view.SurfaceView
import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec.node.AudioSourceNode
import com.rthqks.synapse.exec.node.EncoderNode
import com.rthqks.synapse.exec2.NetworkExecutor
import com.rthqks.synapse.exec2.SurfaceViewNode
import com.rthqks.synapse.logic.Link
import com.rthqks.synapse.logic.Node
import com.rthqks.synapse.polish.Effect
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class EffectExecutor(context: ExecutionContext) : NetworkExecutor(context) {
    private val effect: Effect? = null

    suspend fun swapEffect(effect: Effect) = async {
        if (network == null) {
            network = effect.network
            addAllNodes()
            network?.getNode(Effect.ID_CAMERA)?.properties?.plusAssign(effect.properties)
            addAllLinks()
        } else {
            val old = network!!
            network = effect.network
            val nodesToRemove = mutableSetOf<Node>()
            old.nodes.forEach {
                if (it.key !in STABLE_IDS) {
                    nodesToRemove.add(it.value)
                }
            }
            val nodesToAdd = mutableSetOf<Node>()
            effect.network.nodes.forEach {
                if (it.key !in STABLE_IDS) {
                    nodesToAdd.add(it.value)
                }
            }

            val oldLinks = old.getLinks()
            val newLinks = effect.network.getLinks()
            val linksToRemove = oldLinks - STABLE_LINK
            val linksToAdd = newLinks - STABLE_LINK
            linksToRemove.map { scope.launch { removeLink(it) } }.joinAll()
            nodesToRemove.map { scope.launch { removeNode(it) } }.joinAll()
            nodesToAdd.map { scope.launch { addNode(it) } }.joinAll()
            linksToAdd.map { scope.launch { addLink(it) } }.joinAll()
        }
    }

    override suspend fun release() {
        exec {
            removeAllLinks()
            removeAllNodes()
        }
        super.release()
    }

    suspend fun setSurfaceView(surfaceView: SurfaceView) {
        (getNode(Effect.ID_SURFACE_VIEW) as? SurfaceViewNode)?.setSurfaceView(surfaceView)
    }

    companion object {
        const val TAG = "EffectExecutor"
        val STABLE_IDS = mutableSetOf(
            Effect.ID_CAMERA,
            Effect.ID_MIC,
            Effect.ID_ENCODER,
            Effect.ID_SURFACE_VIEW
        )
        val STABLE_LINK = Link(
            Effect.ID_MIC,
            AudioSourceNode.OUTPUT.id,
            Effect.ID_ENCODER,
            EncoderNode.INPUT_AUDIO.id
        )
    }
}
