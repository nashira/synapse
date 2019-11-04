package xyz.rthqks.synapse.core.edge

import xyz.rthqks.synapse.core.Connection

class TextureConnection(capacity: Int) : Connection<TextureEvent>(capacity) {
    override suspend fun createItem(): TextureEvent = TextureEvent()

}