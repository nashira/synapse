package xyz.rthqks.synapse.core.edge

import android.util.Size
import xyz.rthqks.synapse.core.Connection

class TextureConnection(
    capacity: Int = 1,
    private val textureCreator: suspend () -> TextureEvent
) : Connection<TextureEvent>(capacity) {
    var size = Size(0, 0)
    var isOes = false

    override suspend fun createItem(): TextureEvent = textureCreator()
}