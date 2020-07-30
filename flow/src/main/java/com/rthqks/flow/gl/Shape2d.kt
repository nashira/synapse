package com.rthqks.flow.gl

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

class Shape2d(val sides: Int) : Mesh(
    DRAW_ARRAYS_INSTANCED,
    GLES30.GL_LINE_LOOP,
    0,
    sides,
    instances = 1
) {


    override fun initialize() {
        val byteBuffer = ByteBuffer
            .allocateDirect(4 * 3 * sides)
            .order(ByteOrder.nativeOrder())

        val floatBuffer = byteBuffer.asFloatBuffer()

        val radPerVert = (2.0 * Math.PI) / sides

        for (i in 1..sides) {
            val x = cos(i * radPerVert).toFloat()
            val y = sin(i * radPerVert).toFloat()
            floatBuffer.put(x)
            floatBuffer.put(y)
            floatBuffer.put(0f)
            Log.d(TAG, "vertex $x, $y")
        }

        byteBuffer.position(0)

        val buffer = addBuffer("main", byteBuffer, GLES30.GL_ARRAY_BUFFER, GLES30.GL_STATIC_DRAW)
        addAttribute("position", buffer.id, 3, GLES30.GL_FLOAT, 0, 0, 0)
        super.initialize()
    }

    companion object {
        const val TAG = "Shape2d"
    }
}