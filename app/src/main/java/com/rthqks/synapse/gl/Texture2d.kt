package com.rthqks.synapse.gl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES30.*
import android.opengl.GLUtils

class Texture2d(
    val target: Int = GL_TEXTURE_2D,
    val repeat: Int = GL_CLAMP_TO_EDGE,
    val filter: Int = GL_LINEAR
) {
    var id: Int = 0
        private set

    var width: Int = 0
    var height: Int = 0
    var internalFormat: Int = 0
    var format: Int = 0
    var type: Int = 0

    val matrix = GlesManager.identityMat()
    val oes: Boolean get() = target == GLES11Ext.GL_TEXTURE_EXTERNAL_OES

    fun initialize() {
        val textureHandle = IntArray(1)

        glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {

            glBindTexture(target, textureHandle[0])

            glTexParameteri(target, GL_TEXTURE_MIN_FILTER, filter)
            glTexParameteri(target, GL_TEXTURE_MAG_FILTER, filter)

            glTexParameterf(target, GL_TEXTURE_WRAP_S, repeat.toFloat())
            glTexParameterf(target, GL_TEXTURE_WRAP_T, repeat.toFloat())

            glBindTexture(target, 0)
        } else {
            throw RuntimeException("Error creating texture.")
        }

        id = textureHandle[0]
//        Log.d(TAG, "gen $id")
    }

    fun bind(unit: Int) {
        glActiveTexture(unit)
        glBindTexture(target, id)
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
        format: Int,
        type: Int,
        buffer: java.nio.Buffer? = null
    ) {
        this.width = width
        this.height = height
        this.internalFormat = internalFormat
        this.format = format
        this.type = type

        glBindTexture(target, id)
        glTexImage2D(
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
        glBindTexture(target, 0)
    }

    fun initData(level: Int, bitmap: Bitmap, border: Int = 0) {
        glBindTexture(target, id)
        GLUtils.texImage2D(target, level, bitmap, border)
        glBindTexture(target, 0)
    }

    fun updateData(
        level: Int,
        xoffset: Int,
        yoffset: Int,
        width: Int,
        height: Int,
        buffer: java.nio.Buffer
    ) {
        glBindTexture(target, id)
        glTexSubImage2D(
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
        glBindTexture(target, 0)
    }

    companion object {
        const val TAG = "Texture2d"
    }
}