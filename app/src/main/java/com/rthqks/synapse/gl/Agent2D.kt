package com.rthqks.synapse.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Agent2D(numAgents: Int) : Mesh(
    DRAW_ARRAYS_INSTANCED,
    GLES30.GL_POINTS,
    0,
    1,
    instances = numAgents
) {


    override fun initialize() {
        val byteBuffer = ByteBuffer
            .allocateDirect(4)
            .order(ByteOrder.nativeOrder())

        byteBuffer.put(ZEROS)
        byteBuffer.position(0)

        val buffer = addBuffer("main", byteBuffer, GLES30.GL_ARRAY_BUFFER, GLES30.GL_STATIC_DRAW)
        addAttribute("agent", buffer.id, 2, GLES30.GL_UNSIGNED_SHORT, 0, 0, 0)
        super.initialize()
    }

    companion object {
        private val ZEROS = ByteArray(4)
        const val TAG = "Agent2D"
    }
}