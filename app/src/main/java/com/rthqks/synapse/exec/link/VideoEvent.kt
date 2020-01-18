package com.rthqks.synapse.exec.link

import android.opengl.Matrix
import com.rthqks.synapse.gl.Texture

class VideoEvent(
    var texture: Texture = EMPTY_TEXTURE,
    var matrix: FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
) : Event() {
    companion object {
        private val EMPTY_TEXTURE = Texture(0, 0, 0)
    }
}