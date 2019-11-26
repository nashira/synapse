package xyz.rthqks.synapse.exec.edge

import android.opengl.GLES11Ext
import android.util.Size
import android.view.Surface
import xyz.rthqks.synapse.util.SuspendableGet


class VideoConfig(
    val target: Int,
    val width: Int,
    val height: Int,
    val internalFormat: Int,
    val format: Int,
    val type: Int,
    val rotation: Int = 0
) : Config {

    constructor(size: Size, rotation: Int) : this(0, size.width, size.height, 0, 0, 0, rotation)

    val size = Size(width, height)
    val isOes = target == GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    val surface = SuspendableGet<Surface>()
}