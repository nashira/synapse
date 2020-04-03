package com.rthqks.synapse.exec_dep.link

import com.rthqks.synapse.gl.GlesManager
import com.rthqks.synapse.gl.Texture2d

class VideoEvent(
    var texture: Texture2d = EMPTY_TEXTURE,
    var matrix: FloatArray = GlesManager.identityMat()
) : Event() {
    companion object {
        private val EMPTY_TEXTURE = Texture2d()
    }
}