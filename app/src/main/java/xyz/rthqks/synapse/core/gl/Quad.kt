package xyz.rthqks.synapse.core.gl

import android.opengl.GLES32
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Quad {

    companion object {
        private val FULL_RECTANGLE_COORDS = floatArrayOf(
            -1.0f, -1.0f, // 0 bottom left
            1.0f, -1.0f, // 1 bottom right
            -1.0f, 1.0f, // 2 top left
            1.0f, 1.0f   // 3 top right
        )

        private val FULL_RECTANGLE_TEX_COORDS = floatArrayOf(
            0.0f, 1.0f, // 0 bottom left
            1.0f, 1.0f, // 1 bottom right
            0.0f, 0.0f, // 2 top left
            1.0f, 0.0f  // 3 top right
        )
        val FULL_RECTANGLE_BUF = ByteBuffer
            .allocateDirect(FULL_RECTANGLE_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(FULL_RECTANGLE_COORDS).position(0)

        val FULL_RECTANGLE_TEX_BUF = ByteBuffer
            .allocateDirect(FULL_RECTANGLE_TEX_COORDS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(FULL_RECTANGLE_TEX_COORDS).position(0)

        fun createVao(): Int {
            val vao = IntArray(1)
            GLES32.glGenVertexArrays(1, vao, 0)

            FULL_RECTANGLE_BUF.position(0)
            FULL_RECTANGLE_TEX_BUF.position(0)
            val buffers = IntArray(2)
            GLES32.glGenBuffers(2, buffers, 0)
            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, buffers[0])
            GLES32.glBufferData(
                GLES32.GL_ARRAY_BUFFER,
                FULL_RECTANGLE_COORDS.size * 4,
                FULL_RECTANGLE_BUF,
                GLES32.GL_STATIC_DRAW
            )

            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, buffers[1])
            GLES32.glBufferData(
                GLES32.GL_ARRAY_BUFFER,
                FULL_RECTANGLE_TEX_COORDS.size * 4,
                FULL_RECTANGLE_TEX_BUF,
                GLES32.GL_STATIC_DRAW
            )

            GLES32.glBindVertexArray(vao[0])

            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, buffers[0])
            GLES32.glEnableVertexAttribArray(0)
            GLES32.glVertexAttribPointer(
                0, 2, GLES32.GL_FLOAT, false, 0, 0
            )

            GLES32.glBindBuffer(GLES32.GL_ARRAY_BUFFER, buffers[1])
            GLES32.glEnableVertexAttribArray(1)
            GLES32.glVertexAttribPointer(
                1, 2, GLES32.GL_FLOAT, false, 0, 0
            )

//            GLES32.glBindVertexArray(0)
            Log.d(TAG, "created vao ${vao[0]}")

            return vao[0]
        }

        private val TAG = Quad::class.java.simpleName
    }
}