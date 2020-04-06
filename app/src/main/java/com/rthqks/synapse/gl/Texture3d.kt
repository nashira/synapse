package com.rthqks.synapse.gl

import android.opengl.GLES30.*

class Texture3d(
    val repeat: Int = GL_CLAMP_TO_EDGE,
    val filter: Int = GL_LINEAR
) {
    var id: Int = 0
        private set

    var width: Int = 0
    var height: Int = 0
    var depth: Int = 0
    var internalFormat: Int = 0
    var format: Int = 0
    var type: Int = 0

    // used slice3d
    var index = 0

    fun initialize() {
        val textureHandle = IntArray(1)

        glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {

            glBindTexture(GL_TEXTURE_3D, textureHandle[0])

            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, filter)
            glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, filter)

            glTexParameterf(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, repeat.toFloat())
            glTexParameterf(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, repeat.toFloat())
            glTexParameterf(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, repeat.toFloat())

            glBindTexture(GL_TEXTURE_3D, 0)
        } else {
            throw RuntimeException("Error creating texture.")
        }

        id = textureHandle[0]
//        Log.d(TAG, "gen $id")
    }

    fun bind(unit: Int) {
        glActiveTexture(unit)
        glBindTexture(GL_TEXTURE_3D, id)
    }

    fun release() {
        glDeleteTextures(1, intArrayOf(id), 0)
//        Log.d(TAG, "rel $id")
    }

    fun initData(
        level: Int,
        internalFormat: Int,
        width: Int,
        height: Int,
        depth: Int,
        format: Int,
        type: Int,
        buffer: java.nio.Buffer? = null
    ) {
        this.width = width
        this.height = height
        this.depth = depth
        this.internalFormat = internalFormat
        this.format = format
        this.type = type

        glBindTexture(GL_TEXTURE_3D, id)
        if (buffer == null) {
            glTexImage3D(
                GL_TEXTURE_3D,
                level,
                internalFormat,
                width,
                height,
                depth,
                0,
                format,
                type,
                0
            )
        } else {
            glTexImage3D(
                GL_TEXTURE_3D,
                level,
                internalFormat,
                width,
                height,
                depth,
                0,
                format,
                type,
                buffer
            )
        }
        glBindTexture(GL_TEXTURE_3D, 0)
    }

    fun updateData(
        level: Int,
        xoffset: Int,
        yoffset: Int,
        zoffset: Int,
        width: Int,
        height: Int,
        depth: Int,
        buffer: java.nio.Buffer
    ) {
        glBindTexture(GL_TEXTURE_3D, id)
        glTexSubImage3D(
            GL_TEXTURE_3D,
            level,
            xoffset,
            yoffset,
            zoffset,
            width,
            height,
            depth,
            format,
            type,
            buffer
        )
        glBindTexture(GL_TEXTURE_3D, 0)
    }

    companion object {
        const val TAG = "Texture3d"
    }
}