package xyz.rthqks.synapse.core.edge

import xyz.rthqks.synapse.core.gl.Texture

data class TextureEvent(
    var texture: Texture,
    var matrix: FloatArray,
    var eos: Boolean = false,
    var count: Int = 0,
    var timestamp: Long = 0
)