package com.rthqks.synapse.gl

import android.opengl.GLES32
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Agent2D(numAgents: Int) : Mesh(
    DRAW_ARRAYS_INSTANCED,
    GLES32.GL_POINTS,
    0,
    2,
    instances = numAgents
) {


    override fun initialize() {
        val byteBuffer = ByteBuffer
            .allocateDirect(4)
            .order(ByteOrder.nativeOrder())

        byteBuffer.put(ZEROS)

        byteBuffer.position(0)
        val buffer = addBuffer("main", byteBuffer, GLES32.GL_ARRAY_BUFFER, GLES32.GL_STATIC_DRAW)
        addAttribute("vertex", buffer.id, 2, GLES32.GL_UNSIGNED_SHORT, 0, 0, 0)
        super.initialize()
    }

    companion object {
        private val ZEROS = ByteArray(4)
        const val TAG = "Agent2D"
    }
}