package com.rthqks.flow.exec_dep.link

import android.opengl.GLES11Ext
import android.util.Size
import android.view.Surface


class VideoConfig(
    val target: Int,
    val width: Int,
    val height: Int,
    val internalFormat: Int,
    val format: Int,
    val type: Int,
    val fps: Int,
    val rotation: Int = 0,
    val offersSurface: Boolean = false
) : Config {

    constructor(size: Size, rotation: Int) : this(0, size.width, size.height, 0, 0, 0, rotation)

    val size = Size(width, height)
    val isOes = target == GLES11Ext.GL_TEXTURE_EXTERNAL_OES

    // set by consumer
    var acceptsSurface = false
}