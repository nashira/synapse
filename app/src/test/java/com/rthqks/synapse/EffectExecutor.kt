package com.rthqks.synapse

import com.rthqks.synapse.exec.ExecutionContext
import com.rthqks.synapse.exec2.NetworkExecutor
import com.rthqks.synapse.polish.Effect

class EffectExecutor(context: ExecutionContext) : NetworkExecutor(context) {
    private val effect: Effect? = null

    suspend fun swapEffect(effect: Effect) = async {
        // compare current effect and new effect
        // build list of operations (disconnectLink, removeNode, addNode, connectLink) to convert from old to new
        // execute list of operations

        if (this.effect == null) {
            network = effect.network

            addAllNodes()
            addAllLinks()
        }
    }

    override suspend fun release() {
        exec {
            removeAllLinks()
            removeAllNodes()
        }
        super.release()
    }

    companion object {
        const val TAG = "EffectExecutor"
    }
}
