package com.rthqks.synapse.gl

import android.opengl.GLES30.*
import android.util.Log

class Framebuffer {
    var id: Int = 0
        private set

    fun initialize(vararg textures: Texture2d) {
        val fbo = IntArray(1)
        glGenFramebuffers(1, fbo, 0)
        id = fbo[0]

        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0])

        val buffers = mutableListOf<Int>()
        textures.forEachIndexed { index, t ->
            glFramebufferTexture2D(
                GL_FRAMEBUFFER,
                GL_COLOR_ATTACHMENT0 + index,
                GL_TEXTURE_2D,
                t.id,
                0
            )
            buffers.add(GL_COLOR_ATTACHMENT0 + index)
        }

        glDrawBuffers(buffers.size, buffers.toIntArray(), 0)

        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            Log.w(TAG, "initialize framebuffer error: $status")
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun initialize(texture3d: Texture3d, layer: Int) {
        val fbo = IntArray(1)
        glGenFramebuffers(1, fbo, 0)
        id = fbo[0]

        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0])

        val buffers = mutableListOf<Int>()
        glFramebufferTextureLayer(
            GL_FRAMEBUFFER,
            GL_COLOR_ATTACHMENT0,
            texture3d.id,
            0,
            layer
        )
        buffers.add(GL_COLOR_ATTACHMENT0)

        glDrawBuffers(buffers.size, buffers.toIntArray(), 0)

        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            Log.w(TAG, "initialize framebuffer error: $status")
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun release() {
        glDeleteFramebuffers(1, intArrayOf(id), 0)
    }

    fun bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, id)
    }

    companion object {
        private val TAG = Framebuffer::class.java.simpleName
    }
}