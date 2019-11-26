package xyz.rthqks.synapse.exec.edge

import android.opengl.GLES11Ext
import android.util.Size


class TextureConfig(
    val target: Int,
    val width: Int,
    val height: Int,
    val internalFormat: Int,
    val format: Int,
    val type: Int
) : Config {
    val size = Size(width, height)
    val isOes = target == GLES11Ext.GL_TEXTURE_EXTERNAL_OES
}