package xyz.rthqks.synapse.gl

import android.opengl.GLES32.*
import android.util.Log

class Framebuffer {
    var id: Int = 0
        private set

    fun initialize(texture: Int) {
        val fbo = IntArray(1)
        glGenFramebuffers(1, fbo, 0)
        id = fbo[0]

        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0])
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0)

        val status = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if(status != GL_FRAMEBUFFER_COMPLETE) {
            Log.d(TAG, "initialize framebuffer error: $status")
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