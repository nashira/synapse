package xyz.rthqks.synapse.core.gl

import android.opengl.GLES32
import java.nio.ByteBuffer

class Texture(
    val target: Int,
    val unit: Int,
    val repeat: Int,
    val filter: Int
) {

    var id: Int = 0
        private set

    fun initialize() {
        val textureHandle = IntArray(1)

        GLES32.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {

            GLES32.glBindTexture(target, textureHandle[0])

            GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_MIN_FILTER, filter)
            GLES32.glTexParameteri(target, GLES32.GL_TEXTURE_MAG_FILTER, filter)

            GLES32.glTexParameterf(target, GLES32.GL_TEXTURE_WRAP_S, repeat.toFloat())
            GLES32.glTexParameterf(target, GLES32.GL_TEXTURE_WRAP_T, repeat.toFloat())

            GLES32.glBindTexture(target, 0)
        } else {
            throw RuntimeException("Error creating texture.")
        }

        id = textureHandle[0]
    }

    fun release() {
        GLES32.glDeleteTextures(1, intArrayOf(id), 0)
    }

    fun initData(
        level: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int,
        buffer: ByteBuffer?
    ) {

        GLES32.glActiveTexture(unit)
        GLES32.glBindTexture(target, id)
        GLES32.glTexImage2D(
            target,
            level,
            internalFormat,
            width,
            height,
            0,
            format,
            type,
            buffer
        )
        GLES32.glBindTexture(target, 0)
    }

    fun updateData(
        level: Int,
        xoffset: Int,
        yoffset: Int,
        width: Int,
        height: Int,
        format: Int,
        type: Int,
        buffer: ByteBuffer
    ) {
        GLES32.glActiveTexture(unit)
        GLES32.glBindTexture(target, id)
        GLES32.glTexSubImage2D(
            target,
            level,
            xoffset,
            yoffset,
            width,
            height,
            format,
            type,
            buffer
        )
        GLES32.glBindTexture(target, 0)
    }
}