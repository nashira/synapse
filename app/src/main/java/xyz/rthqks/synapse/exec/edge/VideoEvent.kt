package xyz.rthqks.synapse.exec.edge

import android.opengl.Matrix
import xyz.rthqks.synapse.gl.Texture

class VideoEvent(
    var texture: Texture = Texture(0, 0, 0),
    var matrix: FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
) : Event()