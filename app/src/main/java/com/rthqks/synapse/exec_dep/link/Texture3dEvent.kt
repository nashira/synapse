package com.rthqks.flow.exec_dep.link

import com.rthqks.flow.gl.GlesManager
import com.rthqks.flow.gl.Texture3d

class Texture3dEvent(
    var texture: com.rthqks.flow.gl.Texture3d = EMPTY_TEXTURE,
    var matrix: FloatArray = com.rthqks.flow.gl.GlesManager.identityMat(),
    var index: Int = 0
) : Event() {
    companion object {
        private val EMPTY_TEXTURE = com.rthqks.flow.gl.Texture3d()
    }
}