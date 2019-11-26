package xyz.rthqks.synapse.core.edge

import android.opengl.Matrix
import xyz.rthqks.synapse.gl.Texture

class VideoEvent(
    var texture: Texture = Texture(0, 0, 0),
    var matrix: FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) },
    var eos: Boolean = false,
    var count: Int = 0,
    var timestamp: Long = 0
) : Event()