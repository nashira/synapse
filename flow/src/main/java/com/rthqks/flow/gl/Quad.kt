package com.rthqks.flow.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Quad : Mesh(
    DRAW_ARRAYS,
    GLES30.GL_TRIANGLE_STRIP,
    0,
    4
) {

    lateinit var floatBuffer: FloatBuffer
    lateinit var buffer: Buffer

    override fun initialize() {
        val byteBuffer = ByteBuffer
            .allocateDirect(FULL_RECTANGLE_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())

        floatBuffer = byteBuffer
            .asFloatBuffer()
            .put(FULL_RECTANGLE_COORDS)

        byteBuffer.position(0)
        buffer = addBuffer("main", byteBuffer, GLES30.GL_ARRAY_BUFFER, GLES30.GL_STATIC_DRAW)
        addAttribute("vertex", buffer.id, 2, GLES30.GL_FLOAT, 16, 0, 0)
        addAttribute("texture", buffer.id, 2, GLES30.GL_FLOAT, 16, 8, 1)
        super.initialize()
    }

    companion object {
        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1.0f, -1.0f, 0.0f, 0.0f, // 0 bottom left
            1.0f, -1.0f, 1.0f, 0.0f, // 1 bottom right
            -1.0f, 1.0f, 0.0f, 1.0f, // 2 top left
            1.0f, 1.0f, 1.0f, 1.0f  // 3 top right
        )

        private val TAG = Quad::class.java.simpleName
    }
}