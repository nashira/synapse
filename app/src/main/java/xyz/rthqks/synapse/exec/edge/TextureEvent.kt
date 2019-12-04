package xyz.rthqks.synapse.exec.edge

import xyz.rthqks.synapse.gl.Texture

class TextureEvent(
    var texture: Texture,
    var matrix: FloatArray,
    var eos: Boolean = false,
    var count: Int = 0,
    var timestamp: Long = 0
) : Event()