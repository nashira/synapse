package com.rthqks.flow.exec_dep.link

import com.rthqks.flow.gl.GlesManager
import com.rthqks.flow.gl.Texture2d

class VideoEvent(
    var texture: com.rthqks.flow.gl.Texture2d = EMPTY_TEXTURE,
    var matrix: FloatArray = com.rthqks.flow.gl.GlesManager.identityMat()
) : Event() {
    companion object {
        private val EMPTY_TEXTURE = com.rthqks.flow.gl.Texture2d()
    }
}