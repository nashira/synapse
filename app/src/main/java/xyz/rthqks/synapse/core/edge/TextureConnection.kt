package xyz.rthqks.synapse.core.edge

import android.opengl.GLES11Ext
import android.util.Size
import xyz.rthqks.synapse.core.Connection

class TextureConnection(
    val target: Int,
    val width: Int,
    val height: Int,
    val internalFormat: Int,
    val format: Int,
    val type: Int,
    capacity: Int = 1,
    private val textureCreator: suspend () -> TextureEvent
) : Connection<TextureEvent>(capacity) {
    val size = Size(width, height)
    val isOes = target == GLES11Ext.GL_TEXTURE_EXTERNAL_OES

    override suspend fun createItem(): TextureEvent = textureCreator()
}