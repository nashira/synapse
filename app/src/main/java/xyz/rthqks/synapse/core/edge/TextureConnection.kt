package xyz.rthqks.synapse.core.edge

import android.util.Size
import xyz.rthqks.synapse.core.Connection

class TextureConnection(
    capacity: Int = CAPACITY,
    private val textureCreator: suspend () -> TextureEvent
) : Connection<TextureEvent>(capacity) {
    var size = Size(0, 0)

    override suspend fun createItem(): TextureEvent = textureCreator()
}