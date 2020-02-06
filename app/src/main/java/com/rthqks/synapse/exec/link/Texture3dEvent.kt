package com.rthqks.synapse.exec.link

import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.gl.Texture3d

class Texture3dEvent(
    var texture: Texture3d = EMPTY_TEXTURE,
    var matrix: FloatArray = GlesManager.identityMat()
) : Event() {
    companion object {
        private val EMPTY_TEXTURE = Texture3d()
    }
}